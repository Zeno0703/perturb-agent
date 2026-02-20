Build the agent jar:

```bash
mvn clean package
```

Use it as a Java agent when running your tests:

```bash
mvn test -DargLine="-javaagent:/absolute/path/to/perturbate-1.0-SNAPSHOT.jar -Djunit.jupiter.extensions.autodetection.enabled=true -Dperturb.outDir=target/perturb"
```

Optional: activate one probe id explicitly:

```bash
mvn test -DargLine="-javaagent:/absolute/path/to/perturbate-1.0-SNAPSHOT.jar -Djunit.jupiter.extensions.autodetection.enabled=true -Dperturb.activeProbe=42 -Dperturb.outDir=target/perturb"
```