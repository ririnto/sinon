---
title: Mocking Boundaries and MockK
description: >-
  Open this when mocking boundaries or MockK collaboration checks are the blocker.
---

Open this when the hard part is deciding what stays real, what becomes a test double, and how to verify a Kotlin collaboration boundary with MockK.

## Rules

- mock collaboration boundaries, not simple values
- prefer real value objects and small in-memory fakes when they improve readability
- do not assert internal call choreography unless it is itself the public contract
- if the suite uses MockK, verify only the collaboration that matters

## Pattern

```kotlin
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceClientMockKTest {
    private val client: InvoiceClient = mockk()
    private val service = InvoiceService(client)

    @Test
    fun loadsTheRemoteInvoiceOnce() {
        every { client.load("inv-1") } returns Invoice("inv-1")

        assertEquals("inv-1", service.load("inv-1").id)
        verify(exactly = 1) { client.load("inv-1") }
    }
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mocking simple values or pure helpers | the test fixture becomes noisier than the behavior | keep the simple pieces real |
| verifying every call just because a mock exists | the test couples to implementation choreography | verify only the collaboration that defines the contract |
