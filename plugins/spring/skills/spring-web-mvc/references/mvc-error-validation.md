---
title: Spring Web MVC Validation and Error Recipes
description: >-
  Deeper exception mapping recipes, validation exception splits, and `@ModelAttribute` form binding nuance.
---

Use this reference when the endpoint shape is clear but deeper exception handling, validation split, or form-binding nuance is needed.

## Exception Mapping Recipe

Use `ResponseEntityExceptionHandler` when the goal is one global MVC error shape with RFC 9457 semantics. Override the built-in handlers for validation paths and use narrow `@ExceptionHandler` methods for domain exceptions that need a specific HTTP status.

```java
@RestControllerAdvice
class ApiErrorHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setTitle("Request validation failed");
        body.setDetail("One or more request fields are invalid.");
        body.setProperty("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "rejectedValue", error.getRejectedValue(),
                        "message", error.getDefaultMessage()))
                .toList());
        body.setProperty("globalErrors", ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> Map.of(
                        "object", error.getObjectName(),
                        "message", error.getDefaultMessage()))
                .toList());

        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, Object>> parameterErrors = new ArrayList<>();
        ex.visitResults(new HandlerMethodValidationException.Visitor() {
            @Override
            public void requestParam(RequestParam requestParam, ParameterValidationResult result) {
                parameterErrors.add(Map.of(
                        "parameter", result.getMethodParameter().getParameterName(),
                        "messages", result.getResolvableErrors().stream()
                                .map(MessageSourceResolvable::getDefaultMessage)
                                .toList()));
            }

            @Override
            public void other(ParameterValidationResult result) {
                parameterErrors.add(Map.of(
                        "parameter", result.getMethodParameter().getParameterName(),
                        "messages", result.getResolvableErrors().stream()
                                .map(MessageSourceResolvable::getDefaultMessage)
                                .toList()));
            }
        });

        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setTitle("Method validation failed");
        body.setDetail("One or more request parameters are invalid.");
        body.setProperty("parameterErrors", parameterErrors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleNotFound(UserNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
```

## Validation Exception Split

- `MethodArgumentNotValidException` is the normal path for `@Valid @RequestBody` or `@ModelAttribute` object validation.
- `HandlerMethodValidationException` is the method-parameter path for constraints such as `@NotBlank` on `@RequestParam`, `@PathVariable`, or similar direct method parameters.

## Field and Global Error Rule

- Use `getFieldErrors()` for field-level validation problems that a client can attach to one input field.
- Use `getGlobalErrors()` for object-level validation problems that do not belong to one field.

## Method-Parameter Validation Recipe

Direct constraints on method parameters do not raise `MethodArgumentNotValidException`; they flow through `HandlerMethodValidationException` instead.

```java
@GetMapping("/{id}")
OrderResponse findOne(
        @PathVariable @Positive Long id,
        @RequestParam(defaultValue = "false") boolean includeHistory) {
    return service.findOne(id, includeHistory);
}
```

## `@ModelAttribute` Form Binding Recipe

Use `@ModelAttribute` when the request carries HTML form data rather than a JSON body. Spring binds individual form fields to the model object and runs Bean Validation before the method body executes. Take `BindingResult` as an **immediately following** parameter to handle errors in-method instead of via a global exception handler.

```java
@PostMapping("/orders")
String submitOrder(@Valid @ModelAttribute OrderFormRequest form, BindingResult result, Model model) {
    if (result.hasErrors()) {
        model.addAttribute("form", form);
        return "order-form";
    }
    service.placeOrder(form);
    return "redirect:/orders";
}
```

`BindingResult` must be declared directly after the bound model object. If it is absent, a validation failure raises `MethodArgumentNotValidException` instead, which is handled globally.

Difference from `@Valid @RequestBody`:

- `@RequestBody` reads a JSON body once and raises `MethodArgumentNotValidException` on failure; there is no per-field re-render path in the same method.
- `@ModelAttribute` binds form fields individually and, when `BindingResult` is present, lets the method decide whether to re-show the form or redirect, keeping the user context in-process.
- Both paths share the same Bean Validation annotations; only the binding source and error-handling flow differ.

## Error Contract Rule

Prefer one stable HTTP error contract built on `ProblemDetail`.

- Put human-oriented summary text in `title` and `detail`.
- Put machine-usable validation collections in stable extension properties such as `fieldErrors`, `globalErrors`, or `parameterErrors`.
- Do not return ad hoc `Map<String, Object>` payloads that change from one controller to another.

## Localization Rule

Validation messages can be localized through the normal Bean Validation and Spring message-resolution path. Keep the error shape stable and let message bundles change the text, not the structure.

## Common Mistakes

- Putting business validation annotations everywhere instead of separating business rules
- Returning different error shapes from different controllers
- Collapsing every validation failure into a single message field and throwing away field detail
- Handling only `MethodArgumentNotValidException` and forgetting direct method-parameter validation

Canonical error-handler and validation templates belong in the parent skill entrypoint, not as a local reference link.
