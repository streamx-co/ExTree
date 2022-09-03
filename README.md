![Java Version](https://img.shields.io/badge/java-%3E%3D%208-success) [![Maven Central](https://img.shields.io/maven-central/v/co.streamx.fluent/ex-tree?label=maven%20central)](https://search.maven.org/search?q=g:%22co.streamx.fluent%22%20AND%20a:%22ex-tree%22)

# ExTree

> Build instructions (*NIX): 
>  - mkdir $HOME/lambda
>  - mvn clean install

# License

ExTree is distributed under the terms of the GNU Affero General Public License with the following clarification and special exception.

*As a special exception, the copyright holders of this library give you permission to link this library with the official [Central Repository](https://search.maven.org/search?q=co.streamx.fluent) artefacts having `co.streamx.fluent` Group Id, and to copy and distribute the resulting executable under terms and conditions of the license of those artefacts, provided that this library is linked only with artefacts having `co.streamx.fluent` Group Id.*

### Release instructions

- export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
- mvn release:clean release:prepare -P release
- mvn release:perform -P release
