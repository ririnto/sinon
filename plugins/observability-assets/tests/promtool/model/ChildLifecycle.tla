------------------------- MODULE ChildLifecycle -------------------------
EXTENDS Naturals, Sequences, TLC
CONSTANTS None
Signals == {"INT", "TERM"}
VARIABLES pid, spawn, child, spawnError, closeResult, interrupted, firstSeen,
          listeners, deferred, kills, events, expected, result
vars == <<pid, spawn, child, spawnError, closeResult, interrupted, firstSeen,
          listeners, deferred, kills, events, expected, result>>
Init ==
  /\ pid \in BOOLEAN /\ spawn = "pending" /\ child = "starting"
  /\ spawnError = FALSE /\ closeResult = None
  /\ interrupted = None /\ firstSeen = None /\ listeners = Signals
  /\ deferred = FALSE /\ kills = <<>> /\ events = <<>> /\ expected = <<>> /\ result = None
Signal(s) ==
  /\ s \in listeners /\ result = None
  /\ listeners' = listeners \ {s}
  /\ interrupted' = IF interrupted = None THEN s ELSE interrupted
  /\ firstSeen' = IF firstSeen = None THEN s ELSE firstSeen
  /\ deferred' = IF pid THEN deferred ELSE TRUE
  /\ kills' = IF pid THEN Append(kills, <<"immediate", s, s>>) ELSE kills
  /\ events' = Append(events, <<s, pid>>)
  /\ expected' = IF pid THEN Append(expected, <<"immediate", s>>) ELSE expected
  /\ UNCHANGED <<pid, spawn, child, spawnError, closeResult, result>>
Spawn ==
  /\ spawn = "pending" /\ child = "starting"
  /\ spawn' = "emitted" /\ pid' = TRUE /\ child' = "running"
  /\ kills' = IF deferred THEN Append(kills, <<"deferred", interrupted, firstSeen>>) ELSE kills
  /\ deferred' = FALSE
  /\ expected' = IF Len(events) = 0 THEN expected
                 ELSE IF ~events[1][2] THEN Append(expected, <<"deferred", events[1][1]>>)
                 ELSE expected
  /\ UNCHANGED <<spawnError, closeResult, interrupted, firstSeen, listeners, events, result>>
SpawnError ==
  /\ result = None /\ ~spawnError
  /\ spawnError' = TRUE
  /\ spawn' = IF ~pid /\ spawn = "pending" THEN "impossible" ELSE spawn
  /\ UNCHANGED <<pid, child, closeResult, interrupted, firstSeen, listeners, deferred, kills, events, expected, result>>
Close(r) ==
  /\ child # "closed" /\ (spawn = "emitted" \/ spawnError)
  /\ r \in {"code", "signal"}
  /\ child' = "closed" /\ closeResult' = r
  /\ UNCHANGED <<pid, spawn, spawnError, interrupted, firstSeen, listeners, deferred, kills, events, expected, result>>
Finish ==
  /\ child = "closed" /\ result = None
  /\ result' = IF spawnError THEN "error"
                ELSE IF interrupted # None THEN interrupted
                ELSE closeResult
  /\ listeners' = {} /\ deferred' = FALSE
  /\ UNCHANGED <<pid, spawn, child, spawnError, closeResult, interrupted, firstSeen, kills, events, expected>>
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
  /\ Len(events) <= 2 /\ \A i \in 1..Len(events) : events[i] \in Signals \X BOOLEAN
  /\ Len(expected) <= 2
  /\ \A i \in 1..Len(expected) : expected[i] \in {"immediate", "deferred"} \X Signals
  /\ result \in Signals \cup {None, "error", "code", "signal"}
FirstSignal == interrupted = firstSeen
Forwarding ==
  /\ Len(kills) = Len(expected)
  /\ \A i \in 1..Len(kills) :
       /\ <<kills[i][1], kills[i][2]>> = expected[i]
       /\ kills[i][2] = kills[i][3]
NoEarlyResult == result # None => child = "closed"
Cleanup == result # None => listeners = {} /\ ~deferred
ResultPriority == result # None =>
  result = IF spawnError THEN "error" ELSE IF firstSeen # None THEN firstSeen ELSE closeResult
=============================================================================
