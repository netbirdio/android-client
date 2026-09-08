# Instrumented tests

On-device e2e tests for the Android client, run on an emulator or a real
device. The test group is selected with the instrumentation runner's
`class` argument. The optional `FailFast` listener
(`listener=io.netbird.client.e2e.FailFast`) skips everything after the
first failure.

## Groups (suite classes)

| `class` value | What it runs |
|---|---|
| `io.netbird.client.e2e.E2eSuite` | The full suite, in order: auth, peer connectivity, ACL, DNS, exit node, network transitions. Turns force relay OFF once at start. |
| `io.netbird.client.e2e.NetworkTransitionSuite` | Only the network transition scenarios (exit-node variant + the transition matrix). The member tests turn force relay ON themselves. |

Any single test class below also works as a `class` value.

## Test classes

| Class | What it verifies |
|---|---|
| `SetupKeyAuthTest` | Login through the profile editor UI. |
| `PeerConnectivityTest` | Ping to a live peer over the tunnel, with and without relay support. |
| `PortAclTest` | ACL enforcement: TCP 80 reachable, ICMP blocked on the same peer. |
| `DnsResolutionTest` | Tunnel DNS: peer FQDN and search-domain name resolve to the private IP. |
| `ExitNodeRouteTest` | Public egress goes through the exit node. |
| `ExitNodeNetworkTransitionTest` | Exit-node egress re-establishes after WiFi loss and a full blackout, within tight time budgets. |
| `NetworkTransitionTest` | Network transition scenarios (airplane mode on/off from WiFi/cellular/both, transport switches, handover); recovery must complete within tight time budgets. |

Not part of any suite: `NetworkConnectivityStressTest` (manual, random
disruption stress cycles) and the `ExampleInstrumentedTest` scaffolding.
