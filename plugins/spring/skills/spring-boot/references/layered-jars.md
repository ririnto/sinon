# Spring Boot layered jars

Open this reference when container rebuild speed depends on jar layers.

```bash
java -Djarmode=layertools -jar app.jar extract
```

Enable layers when container rebuild speed matters.

## Validation rule

Verify the produced jar actually contains the intended layer structure before changing the image pipeline.
