# Poker Indien — Blind Man's Bluff (JADE)

Jeu de Poker Indien en 1 contre 1 implemente avec le framework multi-agents JADE.
Deux agents autonomes s'affrontent en utilisant des communications ACL pour echanger
des informations et prendre des decisions strategiques.

---

## Regles du jeu

Le Poker Indien (Blind Man's Bluff) est un jeu a **information imparfaite** :

1. Chaque joueur recoit **une carte** placee sur son front — il ne voit pas sa propre carte.
2. Chaque joueur **voit la carte de l'adversaire**.
3. A chaque manche, les deux joueurs misent une ante obligatoire.
4. Le PLAYER (non-dealer) agit en premier : **fold**, **call** ou **bet**.
5. Le DEALER repond : **fold**, **call** ou **raise**.
6. Si personne ne fold : **showdown** — la carte la plus haute gagne le pot.
7. Les roles s'inversent a chaque manche.
8. La partie se termine quand un joueur n'a plus de jetons.

---

## Structure du projet

```
indian-poker/
├── lib/
│   └── jade.jar              <-- A placer ici manuellement
├── src/
│   └── poker/
│       ├── Main.java          Point d'entree JADE (cree 2 agents)
│       ├── PokerPlayerAgent.java   Agent JADE (dealer + player)
│       ├── DecisionEngine.java     Politique de decision (mock aleatoire)
│       ├── AiRaisonClient.java     Client HTTP ai-raison.com (stub)
│       └── GameConfig.java         Constantes et parametres
├── bin/                       <-- Classes compilees (genere automatiquement)
├── compile.bat                Script de compilation Windows
├── run.bat                    Script de lancement Windows
└── README.md
```

---

## Pre-requis

- **JDK 11+** installe et `java` / `javac` accessibles dans le PATH
- **jade.jar** telecharge depuis [jade.tilab.com](https://jade.tilab.com/)

Verification rapide dans un terminal :
```
java -version
javac -version
```

---

## Installation et lancement

### Etape 1 — Placer jade.jar

Copier `jade.jar` dans le dossier `lib/` du projet :
```
indian-poker/lib/jade.jar
```

### Etape 2 — Compiler

Double-cliquer sur **compile.bat** ou dans un terminal :
```
compile.bat
```

### Etape 3 — Lancer

Double-cliquer sur **run.bat** ou dans un terminal :
```
run.bat
```

### Alternative — Ligne de commande manuelle

```bash
javac -encoding UTF-8 -cp "lib\jade.jar" -d bin src\poker\*.java
java -cp "lib\jade.jar;bin" poker.Main
```

### Alternative — Lancement via jade.Boot (sans Main.java)

```bash
java -cp "lib\jade.jar;bin" jade.Boot -gui "Agent1:poker.PokerPlayerAgent(Agent2,dealer);Agent2:poker.PokerPlayerAgent(Agent1,player)"
```

---

## Architecture des agents

### Protocole de communication ACL

Les 2 agents communiquent exclusivement via des messages JADE/ACL.
Le dealer est la source de verite pour l'etat du jeu.

```
       DEALER                          PLAYER
         |                                |
         |--- REQUEST (DEAL) ------------>|   carte visible, pot, stacks
         |                                |
         |<-- PROPOSE (ACTION) ---------- |   fold/call/bet + montant + timestamp
         |                                |
         |   [decision du dealer]         |
         |                                |
         |--- INFORM (RESULT) ----------->|   gagnant, cartes, stacks finaux
         |                                |
         |   [swap des roles]             |
         |                                |
```

### Format des messages

| Phase | Performatif | Contenu |
|-------|------------|---------|
| Distribution | `REQUEST` | `DEAL\|carteVisible\|pot\|stackDealer\|stackPlayer` |
| Action | `PROPOSE` | `ACTION\|fold/call/bet\|montant\|timestamp` |
| Resultat | `INFORM` | `RESULT\|gagnant\|carteDealer\|cartePlayer\|pot\|stackD\|stackP` |

### ConversationId

Chaque manche est identifiee par `poker-round-N` pour eviter le croisement
de messages entre manches consecutives.

---

## Politique de decision

Le `DecisionEngine` implemente la table de decision de ai-raison.com :

| Contexte | Decision |
|----------|----------|
| `high_opponent_card` | fold |
| `high_opponent_card` + `strong_opponent_bet` | fold |
| `high_opponent_card` + `low_opponent_bet` + `adv_slow_response` | fold |
| `low_opponent_card` + `low_opponent_bet` | bet |
| `low_opponent_card` + `low_opponent_bet` + `big_pot` | bet |
| `low_opponent_card` + `strong_opponent_bet` | call |
| `low_opponent_card` + `strong_opponent_bet` + `big_pot` | call |
| `low_opponent_card` + `strong_opponent_bet` + `adv_fast_response` | call |
| `low_stack` | fold (60%) / bet (40%) |

### Couche de bluff

Par-dessus la decision brute, quatre politiques de bluff sont appliquees :

1. **Kamikaze** — Mise agressive malgre une carte adverse forte (taux adaptatif).
2. **Leurre** — Call au lieu de bet sur carte adverse faible (20% du temps).
3. **Desespere** — All-in quand le stack tombe sous 5 jetons.
4. **Bluff debutant** — Aucun bluff pendant les 10 premieres manches.

Le taux de bluff s'adapte en fonction du nombre de pertes recentes
et du taux de reussite des bluffs precedents.

### Temps comme signal

Le delai de reponse est utilise comme element strategique :
- **Reponse rapide** + bet = intimidation
- **Reponse lente** + call = hesitation simulee

Le dealer mesure le temps entre l'envoi de la donne et la reception
de l'action du player pour alimenter le contexte de decision.

---

## Integration ai-raison.com

Pour remplacer le mock par l'API reelle :

1. Ouvrir `DecisionEngine.java`
2. Dans la methode `decide()`, remplacer l'appel a `applyPolicy()` par :
   ```java
   String elements = buildElementsString(highCard, strongBet, bigPot, lowStack, fastResp, slowResp);
   String decision = AiRaisonClient.query(elements);
   ```
3. Configurer l'URL et la cle API dans `AiRaisonClient.java`
4. Recompiler (`compile.bat`)

---

## Configuration

Tous les parametres sont centralises dans `GameConfig.java` :

| Parametre | Valeur | Description |
|-----------|--------|-------------|
| `STARTING_STACK` | 50 | Jetons initiaux par agent |
| `ANTE` | 1 | Mise obligatoire par manche |
| `HIGH_CARD_THRESHOLD` | 8 | Carte >= 8 = "haute" |
| `STRONG_BET_THRESHOLD` | 5 | Mise >= 5 = "forte" |
| `BIG_POT_THRESHOLD` | 15 | Pot >= 15 = "gros" |
| `LOW_STACK_THRESHOLD` | 10 | Stack <= 10 = "faible" |

---

## Pourquoi ton ancien code ne creait qu'un agent

Le fichier `PokerPlayerAgent.java` original n'avait pas de `Main` pour
demarrer JADE. Sans appel a `Runtime.instance().createMainContainer()` et
deux `createNewAgent()` distincts, JADE ne sait pas qu'il faut instancier
deux agents. Le `CyclicBehaviour` attendait des messages qui n'arrivaient
jamais puisqu'il n'y avait pas de second agent pour les envoyer.
