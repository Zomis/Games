let useGameJs = false
let gamejs = undefined
if (useGameJs) { // TODO: This condition doesn't quite work, it's required anyway during build process
    // gamejs = require("../../games-js/web/games-js");
    if (typeof gamejs["games-js"] !== "undefined") {
        // This is needed when doing a production build, but is not used for `npm run dev` locally.
        gamejs = gamejs["games-js"];
    }
}
function dsl(lookup) {
    if (gamejs) {
        return lookup(gamejs)
    }
    return true
}

import PlayGame from "@/components/PlayGame";

import Alchemists from "@/components/games/alchemists/Alchemists";
import Grizzled from "@/components/games/grizzled/Grizzled";
import Backgammon from "@/components/games/paths/Backgammon";
import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import DungeonMayhem from "@/components/games/dungeon-mayhem/DungeonMayhem"
import UTTT from "@/components/games/UTTT";
import Coup from "@/components/games/Coup";
import KingDomino from "@/components/games/grids/KingDomino";
import Hanabi from "@/components/games/Hanabi";
import HanabiConfig from "@/components/games/hanabi/HanabiConfig";
import TTTUpgrade from "@/components/games/TTTUpgrade";
import Splendor from "@/components/games/splendor/Splendor";
//import TreeViewGame from "@/components/games/TreeViewGame";
import SetGame from "@/components/games/set/SetGame";
import Decrypto from "@/components/games/words/Decrypto";
import Dixit from "@/components/games/Dixit";
import DixitRound from "@/components/games/DixitRound";
import Skull from "@/components/games/skull/Skull";
import DSLTTT from "@/components/games/DSLTTT";
import TTT3D from "@/components/games/TTT3D";
import LiarsDice from "@/components/games/LiarsDice";
import Avalon from "@/components/games/Avalon";
import SpiceRoad from "@/components/games/spiceRoad/SpiceRoad";

// ViewTypes for ActionLog
import SplendorCard from "@/components/games/splendor/SplendorCard"
import SplendorNoble from "@/components/games/splendor/SplendorNoble"
import HanabiCard from "@/components/games/HanabiCard"
import DungeonMayhemCard from "@/components/games/dungeon-mayhem/DungeonMayhemCard"

const defaultRouteProps = route => ({
    gameInfo: route.params.gameInfo,
    showRules: true
})

const xyAction = (data) => data.x + ',' + data.y
const tttActions = {
    play: xyAction
}
const tttMoveActions = {
    move: (source) => ({
        key: source.x + ',' + source.y,
        next: (target) => xyAction(target)
    })
}

const splendorActions = {
    buy: (card) => 'buy-' + card,
    buyReserved: (card) => 'buyReserved-' + card,
    discardMoney: (money) => 'discardMoney-' + money,
    takeMoney: (target1) => ({
        key: 'take-' + target1,
        next: (target2) => ({
            key: 'take-' + target2,
            next: (target3) => 'take-' + target3
        })
    }),
    reserve: (card) => 'reserve-' + card
}

function paySpice(spice) {
    return {
        key: 'pay-' + spice,
        next: paySpice
    }
}

function upgradeSpice(spice) {
  return {
    key: spice,
    next: (amount) => ({
      key: amount,
      next: upgradeSpice
    })
  }
}


const supportedGames = {
    "Alchemists": {
        enabled: false,
        component: Alchemists,
        playTime: '60',
        amountOfPlayers: '2-4'
    },
    "Grizzled": {
        enabled: true,
        component: Grizzled,
        playTime: '45',
        amountOfPlayers: '2-5'
    },
    "Backgammon": {
        component: Backgammon,
        playTime: '15',
        amountOfPlayers: '2'
    },
    "King Domino": {
        component: KingDomino,
        playTime: '45',
        amountOfPlayers: '2-4'
    },
    "Dixit": {
        viewTypes: {
            round: { component: DixitRound, binds: (v) => ({ results: v }) }
        },
        component: Dixit,
        playTime: '30',
        amountOfPlayers: '3-12',
    },
    "TTTUpgrade": {
        component: TTTUpgrade
    },
    "Avalon": {
        dsl: dsl(g => g.net.zomis.games.impl.ResistanceAvalonGame.game),
        component: Avalon,
        playTime: '30',
        amountOfPlayers: '5-10',
    },
    "DSL-UR": {
        displayName: "Royal Game of UR",
        dsl: dsl(g => g.net.zomis.games.impl.DslUR.gameUR),
        component: RoyalGameOfUR,
        playTime: '30',
        amountOfPlayers: '2',
    },
    "Decrypto": {
        enabled: false,
        actions: {
            chat: () => "chat",
            guessCode: () => "guessCode",
            giveClue: () => "giveClue"
        },
        component: Decrypto,
        playTime: '15-45',
        amountOfPlayers: '3-8',
    },
    "Coup": {
        component: Coup,
        playTime: '15',
        amountOfPlayers: '2-6',
    },
    "Hanabi": {
        dsl: dsl(g => g.net.zomis.games.impl.HanabiGame.game),
        configComponent: HanabiConfig,
        actions: {
            Play: (index) => "play-" + index,
            Discard: (index) => "discard-" + index,
            GiveClue: (playerIndex) => ({
                key: 'player-' + playerIndex,
                next: (mode) => ({
                    key: 'giveclue-' + mode,
                    next: (hintValue) => `giveclue-${mode}-${hintValue}`
                })
            })
        },
        viewTypes: {
            card: { component: HanabiCard, binds: (v) => ({ card: v, doubleView: true }) }
        },
        component: Hanabi,
        playTime: '25',
        amountOfPlayers: '2-5',
    },
    "Set": {
        resetActions: false,
        component: SetGame,
        playTime: '30',
        amountOfPlayers: '1-20',
    },
    "Splendor": {
        dsl: dsl(g => g.net.zomis.games.impl.DslSplendor.splendorGame),
        actions: splendorActions,
        component: Splendor,
        viewTypes: {
            card: { component: SplendorCard, binds: (v) => ({ card: v }) },
            noble: { component: SplendorNoble, binds: (v) => ({ noble: v }) },
        },
        playTime: '30',
        amountOfPlayers: '2-4',
    },
    "Spice Road": {
        actions: {
            play: (card) => ({
                key: card,
                next: upgradeSpice
            }),
            rest: () => "rest",
            claim: (card) => card,
            acquire: (card) => ({
                key: card,
                next: paySpice
            }),
            discard: (spice) => spice
        },
        component: SpiceRoad,
        playTime: '30-45',
        amountOfPlayers: '2-5',
    },
    "DSL-Connect4": {
        displayName: "Connect Four",
        dsl: dsl(g => g.net.zomis.games.impl.ttt.DslTTT.gameConnect4),
        actions: tttActions,
        component: DSLTTT,
    },
    "Quixo": {
        displayName: "Quixo",
        dsl: dsl(g => g.net.zomis.games.impl.TTSourceDestinationGames.gameQuixo),
        actions: tttMoveActions,
        component: DSLTTT,
    },
    "Skull": {
        dsl: dsl(g => g.net.zomis.games.impl.SkullGame.game),
        actions: {
            play: (index) => "hand-" + index,
            bet: (index) => "" + index,
            discard: (index) => "hand-" + index,
            pass: () => "pass",
            chooseNextPlayer: (index) => "choosePlayer-" + index,
            choose: (index) => "choose-" + index
        },
        component: Skull,
        playTime: '15-45',
        amountOfPlayers: '2-6',
    },
    "LiarsDice": {
        displayName: "Liar's Dice",
        dsl: dsl(g => g.net.zomis.games.impl.LiarsDiceGame.game),
        actions: {
            bet: (amount) => ({
                key: amount + ' x ?\'s',
                next: (face) => `bet ${amount}x ${face}'s`
            }),
            liar: () => "liar",
            spotOn: () => "spotOn"
        },
        component: LiarsDice,
        playTime: '15-30',
        amountOfPlayers: '2-20',
    },
    "Dungeon Mayhem": {
        dsl: dsl(g => g.net.zomis.games.impl.DungeonMayhemDsl.game),
        actions: {
            play: (index) => "play-" + index,
            target: (target) => "target:player-" + target.player + ';shield-' + target.shieldCard + ';discarded-' + target.discardedCard
        },
        viewTypes: {
            card: { component: DungeonMayhemCard, binds: (v) => ({ card: v, actions: { available: {} } }) }
        },
        component: DungeonMayhem,
        playTime: '10',
        amountOfPlayers: '2-4',
    },
    "Artax": {
        dsl: dsl(g => g.net.zomis.games.impl.ArtaxGame.gameArtax),
        actions: tttMoveActions,
        component: DSLTTT,
    },
    "DSL-Reversi": {
        displayName: "Reversi",
        dsl: dsl(g => g.net.zomis.games.impl.ttt.DslTTT.gameReversi),
        actions: tttActions,
        component: DSLTTT,
    },
    "DSL-TTT3D": {
        displayName: "3D Tic-Tac-Toe / Connect Four",
        dsl: dsl(g => g.net.zomis.games.impl.ttt.TTT3DGame.game),
        actions: tttActions,
        component: TTT3D,
    },
    "DSL-UTTT": {
        displayName: "Tic-Tac-Toe Ultimate",
        dsl: dsl(g => g.net.zomis.games.impl.ttt.DslTTT.gameUTTT),
        component: UTTT,
    },
    "DSL-TTT": {
        displayName: "Tic-Tac-Toe",
        dsl: dsl(g => g.net.zomis.games.impl.ttt.DslTTT.game),
        actions: tttActions,
        component: DSLTTT,
    }
}

function enabledGames() {
    return Object.keys(supportedGames).filter(key => supportedGames[key].enabled !== false)
}

export default {
    gamejs: gamejs,
    displayName(gameType) {
        let game = supportedGames[gameType]
        return game && game.displayName ? game.displayName : gameType
    },
    enabledGamesTextValue() {
        return this.enabledGameKeys().map(gameType => ({ text: this.displayName(gameType), value: `type/${gameType}`}))
    },
    routes() {
        return enabledGames().map(key => {
            let game = supportedGames[key]
            let routeProps = game.routeProps || defaultRouteProps
            return {
                path: `/games/${key}/:gameId`,
                name: game.routeName || key,
                component: PlayGame,
                props: route => ({
                    gameType: key,
                    gameId: route.params.gameId,
                    ...routeProps(route)
                })
            }
        })
    },
    components() {
        let components = {}
        for (let key of enabledGames()) {
            let game = supportedGames[key]
            let componentName = game.component.name
            components[componentName] = game.component
        }
        return components
    },
    resolveActionKey(supportedGame, actionData, actionChoice) {
        let actionName = actionData.actionType
        let value = actionData.serialized
        if (!supportedGame.actions) return value
        let actionKeys = supportedGame.actions[actionName]
        if (!actionKeys) {
            console.log("No actionKeys set for", actionName, "default to same as serialized value", value)
            return value
        }
        if (actionChoice === null) {
            actionChoice = { choices: [] };
        }
        let choices = actionChoice.choices.slice()
        let a = actionKeys;
        for (let i = 0; i < choices.length; i++) {
            a = a(choices[i]); // Returns either string or { key: string, next: function }
            if (typeof a === 'object' && a.next) {
                a = a.next
            }
        }
        if (typeof a !== 'function') {
            console.error("'a' is not a function", a, actionName, actionData, actionChoice)
            //return a;
            throw a;
        }
        a = a(value)
        if (typeof a === 'object') {
            return a.key
        }
        return a
    },
    enabledGameKeys() {
        return enabledGames()
    },
    games: supportedGames
}
