# Implemented lobby flow

```mermaid
flowchart TD
    Home[Home] -->|Remote: Gather Your Party| Lobby[Lobby: QR and two player slots]
    Home -->|Remote| Help[How to Join]
    Help -->|Back| Home
    Home -->|Remote| Network[Network address and retry]
    Network -->|Back| Previous[Return to previous screen]
    Lobby -->|Back| Home
    Lobby -->|Select player| Manage[Remove controller or return]
    Manage --> Lobby
    Lobby -->|Remote| Network
    QR[Phone scans QR] --> Assign[Assign or reclaim slot]
    Lobby -.-> QR
    Assign -.-> Status[TV and phone connection status]
    Status -.-> Lobby
    Lobby -->|Two phones connected; remote selects Start| Ready[Party assembled confirmation]
    Ready -->|Back| Lobby
```

Phones have connection UI only: room entry for the fallback URL, automatic joining for QR links, player number, connection status, reconnect, and clear rejection messages. They do not have Ready or shared menu controls. The TV's initial focus is always visible; disabled Start is skipped during D-pad navigation.

The party-ready screen is the boundary of this milestone. Character choice, countdown, split-screen gameplay, upgrades, and results remain future work.
