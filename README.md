# KeY IDE common

A bridge between IDE plugins and [KeY](https://key-project.org). It runs KeY without a user
interface on a Java project and provides the functions the plugins share:

- loading the project's Java sources into KeY
- listing the proof obligations of the project's JML contracts
- running proofs in KeY's automatic mode and replaying saved proofs
- reporting for each obligation whether its proof is closed, which contracts the proof uses,
  and which settings of a saved proof differ from the current configuration
- storing the project's KeY settings and its proof files; a proof replaced by a new run is
  moved to a trash directory, which is pruned by size or age as configured

The plugins communicate with the bridge over JSON-RPC and contain no KeY code.

KeY is not bundled. The bridge runs with the user's own `key-*-exe.jar` on the classpath.

Used by [key-intellij-plugin](https://github.com/unp1/key-intellij-plugin) and
[key-vscode-plugin](https://github.com/unp1/key-vscode-plugin).

## Building

Requirements: a JDK 21 or later, and a KeY checkout in a sibling directory named `key`. The
bridge is compiled against KeY's current API, not against a released version.

```
git clone https://github.com/KeYProject/key.git
git clone git@github.com:unp1/key-ide-common.git
cd key-ide-common
./gradlew shadowJar
```

`settings.gradle` includes `../key` as a composite build, so the first build also compiles
KeY and takes a while. The result is

```
build/libs/key-ide-common-0.1.0-dev-all.jar
```

Configure the plugins with this jar and with the `key-*-exe.jar` from the KeY build.

Run the tests with

```
./gradlew test
```

The tests run KeY on a small fixture project in `src/test/fixture` and take about a minute.

## Running the bridge manually

Both entry points take a runtime directory as their only argument:

```
java -cp key-3.1.0-dev-exe.jar:key-ide-common-0.1.0-dev-all.jar org.key_project.ide.server.BridgeMain /tmp/key-ide-1
```

```
java -cp key-ide-common-0.1.0-dev-all.jar org.key_project.ide.server.ConfigBridgeMain /tmp/key-ide-1
```

`BridgeMain` provides all functions and requires KeY on the classpath. `ConfigBridgeMain`
only reads and writes the project's `.key/settings.json` and does not load KeY. The plugins
use it to show the settings before KeY is configured.

Both write the connection address to `<runtime-directory>/endpoint`. Standard output
carries KeY's log. Both exit when the client disconnects, or after five minutes if no client
connects.

## Platforms

Linux, macOS and Windows. The bridge listens on a Unix domain socket in a directory that only
the current user can access. Where Unix domain sockets are not available, it listens on a
loopback TCP port and requires a token, which it writes to the endpoint file together with
the address. For a client that cannot connect to a Unix domain socket, start the bridge with
TCP:

```
java -Dkey.ide.transport=tcp -cp ... org.key_project.ide.server.BridgeMain <runtime-directory>
```

On Windows, proof files are named after their contract and stored under the package
directories of their class, so deeply nested projects can exceed the 260-character path
limit. Keep the project path short or enable long paths.

## Support for KeY-RPC

A planned alternative to starting KeY from a jar is to connect to KeY's own KeY-RPC server.
This requires KeY-RPC to provide:

- [ ] a list of the requests the server implements
- [ ] the taclet and strategy options of a context, with KeY's labels and descriptions
- [ ] loading several saved proofs into one environment, so that KeY can determine the
      dependencies between them
- [ ] proof state across requests: whether a proof is closed, which contracts it uses, and
      the settings it was created with
- [ ] the contracts of the method at a source position, without those of its overloads; the
      target name alone does not distinguish overloads
- [ ] KeY's status icons as image data, for each state and for dark themes, so that the
      plugins ship no KeY assets

## Licence

GPL-2.0-or-later: the GNU General Public License version 2, in [LICENSE](LICENSE), or (at
your option) any later version. The bridge uses KeY's API directly and is therefore
licensed under the same terms as KeY. The plugins are separate programs that communicate
with the bridge over JSON-RPC and do not link KeY. They are licensed under MIT.

Third-party components: Eclipse LSP4J (dual-licensed EPL-2.0 or EDL-1.0, used here under
EDL-1.0, which is BSD-3-Clause) and Gson (Apache-2.0). KeY is supplied by the user and is
not bundled or redistributed.
