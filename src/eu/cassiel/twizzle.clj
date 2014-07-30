(ns eu.cassiel.twizzle
  "The Adventures of Twizzle, a simple timeline automation system.")

(defn initial
  "Initial system state. Takes an optional map of starting values for channels
   which, in front of any fades, we want to be other than zero."
  [& {:keys [init interp]}]
  {:interp interp
   ;; Channels: map from chan-name to {:fades, :current}.
   :channels (reduce-kv (fn [m k v] (assoc m k {:fades nil :current v}))
                        nil
                        init)
   :time 0})

(defn apply-to-channels
  "Apply function to (fades * current) of all channels."
  [state f]
  (update-in state
             [:channels]
             (partial reduce-kv (fn [m k v] (assoc m k (f v))) nil)))

(defn interp-default
  "Default interpolation. Treats first value of `nil` as `0`."
  [val-1 val-2 pos]
  (let [val-1 (or val-1 0)]
    (+ val-1 (* (- val-2 val-1) pos))))

(defn apply-fade
  "Apply a fade to a current value, return new current value (unchanged if fade in the future)."
  [{:keys [start dur target]} ts current]
  (cond (<= ts start)
        current

        (>= ts (+ start dur))
        target

        ;; duration 0? TESTME
        :else
        (interp-default current target (/ (- ts start) dur))))

(defn purge
  "Purge a channel; remove all expired fades, chasing them (and updating `:current`)
   as we go."
  [{:keys [fades current] :as channel} ts]
  (if (empty? fades)
    channel
    (let [[f f'] fades]
      (cond (> (:start f) ts)
            channel

            (< (+ (:start f) (:dur f)) ts)
            (recur {:fades f'
                    :current (:target f)} ts)

            :else
            channel))))

(defn automate
  "Add an automation fade to a channel `ch`. The fade starts at time, `ts`,
   lasts for `dur` frames and fades from the current value to `target`.

   If this fade lies totally in front of the current timestamp, it'll be chased and
   removed; otherwise it'll be interpolated if it's in scope.

   Returns a new state.

   Don't overlap fades. Bad things will happen. (Actually, fades will be applied
   in increasing order of starting stamp.)"
  [state ch start-ts dur target]
  (update-in state
             [:channels ch]
             (fn [{f :fades c :current}]
               (let [f' (sort-by :start (conj f {:start start-ts :dur dur :target target}))
                     ch' (purge {:fades f' :current c} (:time state))]
                 ch'))))

(defn locate
  "Change the location of this state to timestamp `ts`, returning a new state. Expired
   fades will be applied at their last points and purged (so winding time back again
   will not restore them)."
  [state ts]
  (-> state
      (assoc :time ts)
      (apply-to-channels #(purge % ts))))

(defn sample
  "Sample a channel `ch` at the state's current time. Assume purged (i.e. no fades
   are completely in front of the sample point)."
  [{:keys [channels time]} ch]
  (let [{:keys [fades current]} (get channels ch)]
    (if (empty? fades)
      current
      (apply-fade (first fades) time current))))