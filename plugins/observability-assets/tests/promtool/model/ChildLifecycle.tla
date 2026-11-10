------------------------- MODULE ChildLifecycle -------------------------
EXTENDS Naturals, Sequences, TLC
CONSTANTS None
Signals == {"INT", "TERM"}
VARIABLES pid, spawn, child, spawnError, closeResult, interrupted, firstSeen,
          listeners, deferred, kills, result
vars == <<pid, spawn, child, spawnError, closeResult, interrupted, firstSeen,
          listeners, deferred, kills, result>>
Init ==
  /\ pid \in BOOLEAN /\ spawn = "pending" /\ child = "starting"
  /\ spawnError = FALSE /\ closeResult = None
  /\ interrupted = None /\ firstSeen = None /\ listeners = Signals
  /\ deferred = FALSE /\ kills = <<>> /\ result = None
Signal(s) ==
  /\ s \in listeners /\ result = None
  /\ listeners' = listeners \ {s}
  /\ interrupted' = IF interrupted = None THEN s ELSE interrupted
  /\ firstSeen' = IF firstSeen = None THEN s ELSE firstSeen
  /\ deferred' = IF pid THEN deferred ELSE TRUE
  /\ kills' = IF pid THEN Append(kills, <<"immediate", s, s>>) ELSE kills
  /\ UNCHANGED <<pid, spawn, child, spawnError, closeResult, result>>
Spawn ==
  /\ spawn = "pending" /\ child = "starting"
  /\ spawn' = "emitted" /\ pid' = TRUE /\ child' = "running"
  /\ kills' = IF deferred THEN Append(kills, <<"deferred", interrupted, firstSeen>>) ELSE kills
  /\ deferred' = FALSE
  /\ UNCHANGED <<spawnError, closeResult, interrupted, firstSeen, listeners, result>>
SpawnError ==
  /\ result = None /\ ~spawnError
  /\ spawnError' = TRUE
  /\ spawn' = IF ~pid /\ spawn = "pending" THEN "impossible" ELSE spawn
  /\ UNCHANGED <<pid, child, closeResult, interrupted, firstSeen, listeners, deferred, kills, result>>
Close(r) ==
  /\ child # "closed" /\ (spawn = "emitted" \/ spawnError)
  /\ r \in {"code", "signal"}
  /\ child' = "closed" /\ closeResult' = r
  /\ UNCHANGED <<pid, spawn, spawnError, interrupted, firstSeen, listeners, deferred, kills, result>>
Finish ==
  /\ child = "closed" /\ result = None
  /\ result' = IF spawnError THEN "error"
                ELSE IF interrupted # None THEN interrupted
                ELSE closeResult
  /\ listeners' = {} /\ deferred' = FALSE
  /\ UNCHANGED <<pid, spawn, child, spawnError, closeResult, interrupted, firstSeen, kills>>
Next == (\E s \in Signals : Signal(s)) \/ Spawn \/ SpawnError \/ (\E r \in {"code", "signal"} : Close(r)) \/ Finish \/ (result # None /\ UNCHANGED vars)
Spec == Init /\ [][Next]_vars
TypeOK ==
  /\ pid \in BOOLEAN /\ spawn \in {"pending", "emitted", "impossible"}
  /\ child \in {"starting", "running", "closed"} /\ spawnError \in BOOLEAN
  /\ closeResult \in {None, "code", "signal"}
  /\ interrupted \in Signals \cup {None} /\ firstSeen \in Signals \cup {None}
  /\ listeners \subseteq Signals /\ deferred \in BOOLEAN
  /\ Len(kills) <= 2
  /\ \A i \in 1..Len(kills) : kills[i] \in {"immediate", "deferred"} \X Signals \X Signals
  /\ result \in Signals \cup {None, "error", "code", "signal"}
FirstSignal == interrupted = firstSeen
Forwarding == \A i \in 1..Len(kills) : kills[i][2] = kills[i][3]
NoEarlyResult == result # None => child = "closed"
Cleanup == result # None => listeners = {} /\ ~deferred
ResultPriority == result # None =>
  result = IF spawnError THEN "error" ELSE IF firstSeen # None THEN firstSeen ELSE closeResult
=============================================================================
