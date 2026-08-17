# KeY IDE common

Puts [KeY](https://key-project.org) to work on a Java project without a user interface. It
loads the project's sources into KeY, lists what the project can be asked to prove, runs
proofs headlessly, reads saved proofs back, and reports what KeY says about each of them:
whether a proof closes, which contracts it used, and how the settings it was made with
differ from the ones configured now. It also keeps the project's KeY settings, decides
where proofs are stored, and puts a proof a rerun replaced into a trash it prunes.

This is what an IDE integration needs and what KeY is asked for on its behalf. The
integrations run it and exchange plain data with it, so a plugin needs no KeY class of its
own.

KeY is not bundled. The bridge is started with the user's own `key-*-exe.jar` on the
classpath.

Used by: [key-intellij-plugin](https://github.com/unp1/key-intellij-plugin),
[key-vscode-plugin](https://github.com/unp1/key-vscode-plugin).

## Building

Requires a JDK 21 and a KeY checkout next to this one, since the bridge is compiled against
KeY's API rather than against a published release:

```
git clone https://github.com/KeYProject/key.git      # as a sibling directory named "key"
git clone git@github.com:unp1/key-ide-common.git
cd key-ide-common
./gradlew shadowJar
```

`settings.gradle` includes `../key` as a composite build, so the first build also builds
KeY, which takes a while. The result is

```
build/libs/key-ide-common-0.1.0-dev-all.jar
```

which is what a plugin is pointed at, together with a `key-*-exe.jar` from the KeY build.

Tests:

```
./gradlew test
```

They run KeY over a small fixture project in `src/test/fixture`, so a run takes about a
minute.

## Running it by hand

Two mains, both taking the directory a client is to find it through:

```
java -cp key-2.12-exe.jar:key-ide-common-0.1.0-dev-all.jar org.key_project.ide.server.BridgeMain /tmp/key-ide-1
```

```
java -cp key-ide-common-0.1.0-dev-all.jar org.key_project.ide.server.ConfigBridgeMain /tmp/key-ide-1
```

The first does everything above and needs KeY. The second only reads and writes a project's
`.key/settings.json`, and never touches KeY, so an IDE can offer its settings page before
KeY has been configured at all. Both exit when their client goes away, and both wait five
minutes for a first client before giving up.

The address a client connects to is written to `<runtime-directory>/endpoint`, not to
standard output, which carries KeY's log.

## Platforms

Linux, macOS and Windows. The connection is a Unix domain socket in a directory only its
owner can enter; where that is unavailable it falls back to a loopback port and requires the
client to present a token published alongside the address. A client whose runtime cannot
reach a Unix domain socket asks for the port itself:

```
java -Dkey.ide.transport=tcp -cp ... org.key_project.ide.server.BridgeMain <runtime-directory>
```

On Windows, note that a proof file is named after its contract and lands under the package
directories of its class, so a project in a deeply nested directory can approach the 260
character path limit. Keep the project path short, or turn long paths on.

## Adding support for KeY-RPC

Reaching KeY through its own KeY-RPC server, rather than through a jar started here. What
KeY-RPC has to offer before that:

- [ ] which requests the server implements
- [ ] the taclet and strategy options a context offers, with KeY's labels and explanations
- [ ] loading several saved proofs into one environment, so that KeY relates them to each
      other
- [ ] proof state across requests: whether a proof closes, which contracts it used, and the
      settings it was made with
- [ ] the contracts about the method at a source position, told apart from that method's
      overloads, which a target's name does not do
- [ ] KeY's status icons as image data, for the states it draws and for a dark theme, so
      that a client ships none of KeY's assets

## Licence

GPL-2.0-only, in [LICENSE](LICENSE). The bridge calls KeY's API directly, and KeY is
GPL-2.0-only, so this cannot be more liberal. The plugins are separate programs that
exchange plain data with it and link nothing of KeY, which is why they are MIT.

Third-party components: Eclipse LSP4J (dual EPL-2.0 or EDL-1.0, used here under EDL-1.0,
which is BSD-3-Clause) and Gson (Apache-2.0). KeY itself is supplied by the user and is
never bundled or redistributed here.
