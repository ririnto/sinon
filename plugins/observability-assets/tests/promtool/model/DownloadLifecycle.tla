------------------------ MODULE DownloadLifecycle ------------------------
EXTENDS TLC
CONSTANTS Assets, None
VARIABLES phase, firstError, expectedError, aggregate, catchPhase, abortRequested, outcome
vars == <<phase, firstError, expectedError, aggregate, catchPhase, abortRequested, outcome>>
Settled == {"fulfilled", "rejected"}
Init ==
  /\ phase = [a \in Assets |-> "request"]
  /\ firstError = None /\ expectedError = None
  /\ aggregate = "pending"
  /\ catchPhase = "waiting"
  /\ abortRequested = FALSE
  /\ outcome = None
RequestOk(a) ==
  /\ phase[a] = "request"
  /\ phase' = [phase EXCEPT ![a] = "body"]
  /\ UNCHANGED <<firstError, expectedError, aggregate, catchPhase, abortRequested, outcome>>
Reject(a) ==
  /\ phase[a] \in {"request", "body"}
  /\ phase' = [phase EXCEPT ![a] = "rejected"]
  /\ firstError' = IF firstError = None THEN a ELSE firstError
  /\ expectedError' = IF expectedError = None THEN a ELSE expectedError
  /\ UNCHANGED <<aggregate, catchPhase, abortRequested, outcome>>
BodyOk(a) ==
  /\ phase[a] = "body"
  /\ phase' = [phase EXCEPT ![a] = "fulfilled"]
  /\ UNCHANGED <<firstError, expectedError, aggregate, catchPhase, abortRequested, outcome>>
AggregateReject ==
  /\ aggregate = "pending" /\ firstError # None
  /\ aggregate' = "rejected"
  /\ UNCHANGED <<phase, firstError, expectedError, catchPhase, abortRequested, outcome>>
AggregateFulfill ==
  /\ aggregate = "pending" /\ \A a \in Assets : phase[a] = "fulfilled"
  /\ aggregate' = "fulfilled"
  /\ UNCHANGED <<phase, firstError, expectedError, catchPhase, abortRequested, outcome>>
Abort ==
  /\ aggregate = "rejected" /\ catchPhase = "waiting"
  /\ catchPhase' = "draining" /\ abortRequested' = TRUE
  /\ UNCHANGED <<phase, firstError, expectedError, aggregate, outcome>>
Throw ==
  /\ catchPhase = "draining" /\ \A a \in Assets : phase[a] \in Settled
  /\ catchPhase' = "thrown" /\ outcome' = firstError
  /\ UNCHANGED <<phase, firstError, expectedError, aggregate, abortRequested>>
Return ==
  /\ aggregate = "fulfilled" /\ catchPhase = "waiting"
  /\ catchPhase' = "returned" /\ outcome' = "success"
  /\ UNCHANGED <<phase, firstError, expectedError, aggregate, abortRequested>>
Next ==
  \/ \E a \in Assets : RequestOk(a) \/ Reject(a) \/ BodyOk(a)
  \/ AggregateReject \/ AggregateFulfill \/ Abort \/ Throw \/ Return
  \/ /\ catchPhase \in {"thrown", "returned"} /\ UNCHANGED vars
Spec == Init /\ [][Next]_vars
TypeOK ==
  /\ phase \in [Assets -> {"request", "body", "fulfilled", "rejected"}]
  /\ firstError \in Assets \cup {None} /\ expectedError \in Assets \cup {None}
  /\ aggregate \in {"pending", "fulfilled", "rejected"}
  /\ catchPhase \in {"waiting", "draining", "thrown", "returned"}
  /\ abortRequested \in BOOLEAN
  /\ outcome \in Assets \cup {None, "success"}
ReturnSettled == catchPhase = "returned" => \A a \in Assets : phase[a] = "fulfilled"
ThrowDrained == catchPhase = "thrown" => abortRequested /\ \A a \in Assets : phase[a] \in Settled
ErrorPreserved == catchPhase = "thrown" => outcome = expectedError /\ expectedError # None /\ phase[expectedError] = "rejected"
=============================================================================
