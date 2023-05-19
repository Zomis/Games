### Build

`./gradlew :games-compose:common:packageUberJarForCurrentOS`

### Distributing

See https://walczak.it/blog/distributing-javafx-desktop-applications-without-requiring-jvm-using-jlink-and-jpackage

`jlink --module-path outputJarFromAboveGradleCommand.jar --output /output/path --add-modules java.base,java.desktop,java.logging,java.xml`
