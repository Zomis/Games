let useGameJs = false
let gamejs = undefined
if (useGameJs) {
    gamejs = require("../../games-js/web/games-js");
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

import RoyalGameOfUR from "@/components/RoyalGameOfUR";
import DungeonMayhem from "@/components/games/dungeon-mayhem/DungeonMayhem"
import UTTT from "@/components/games/UTTT";
import Coup from "@/components/games/Coup";
import Hanabi from "@/components/games/Hanabi";
import HanabiConfig from "@/components/games/hanabi/HanabiConfig";
import Splendor from "@/components/games/splendor/Splendor";
//import TreeViewGame from "@/components/games/TreeViewGame";
import SetGame from "@/components/games/set/SetGame";
import Decrypto from "@/components/games/words/Decrypto";
import Dixit from "@/components/games/Dixit";
import Skull from "@/components/games/skull/Skull";
import DSLTTT from "@/components/games/DSLTTT";
import TTT3D from "@/components/games/TTT3D";
import LiarsDice from "@/components/games/LiarsDice";
import Avalon from "@/components/games/Avalon";

// ViewTypes for ActionLog
import SplendorCard from "@/components/games/splendor/SplendorCard"
import SplendorNoble from "@/components/games/splendor/SplendorNoble"
import HanabiCard from "@/components/games/HanabiCard"
import DungeonMayhemCard from "@/components/games/dungeon-mayhem/DungeonMayhemCard"

import DslGameState from "./components/games/DslGameState";
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

const setActions = {
    set: (target1) => ({
        key: 'set-' + target1,
        next: (target2) => ({
            key: 'set-' + target2,
            next: (target3) => 'set-' + target3
        })
    })
}

function recursiveAvalon(teamMember) {
    return {
        key: 'players/' + teamMember,
        next: recursiveAvalon
    }
}

const supportedGames = {
    "Dixit": {
        dsl: true,
        actions: {
            story: () => "story",
            place: () => "place",
            vote: (card) => `vote-${card}`,
        },
        component: Dixit
    },
    "Avalon": {
        dsl: dsl(g => g.net.zomis.games.impl.ResistanceAvalonGame.game),
        actions: {
            teamChoice: (missionNumber) => ({
                key: 'mission-' + missionNumber,
                next: recursiveAvalon
            }),
            vote: (result) => `${result}`,
            performMission: (result) => `${result}`,
            assassinate: (targetPlayer) => "players/" + targetPlayer,
            useLadyOfTheLake: (targetPlayer) => "players/" + targetPlayer
        },
        component: Avalon,
    },
    "DSL-UR": {
        displayName: "Royal Game of UR",
        dsl: dsl(g => g.net.zomis.games.impl.DslUR.gameUR),
        actions: {
            roll: () => "roll",
            move: (i) => `${i}`
        },
        component: RoyalGameOfUR,
    },
    "Decrypto": {
        dsl: true,
        actions: {
            chat: () => "chat",
            guessCode: () => "guessCode",
            giveClue: () => "giveClue"
        },
        component: Decrypto
    },
    "Coup": {
        dsl: true,
        actions: {
            lose: (card) => `${card}`,
            reveal: () => "reveal",
            challenge: () => "challenge",
            accept: () => "accept",
            perform: (action) => ({
                key: 'action-' + action,
                next: (target) => `players/${target}`
            }),
            counteract: (counteract) => `counteract-${counteract}`,
            putBack: (card) => `${card}`
        },
        component: Coup
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
    },
    "Set": {
        dsl: true,
        enabled: true,
        resetActions: false,
        actions: setActions,
        component: SetGame,
    },
    "Splendor": {
        dsl: dsl(g => g.net.zomis.games.impl.DslSplendor.splendorGame),
        enabled: true,
        actions: splendorActions,
        component: Splendor,
        viewTypes: {
            card: { component: SplendorCard, binds: (v) => ({ card: v }) },
            noble: { component: SplendorNoble, binds: (v) => ({ noble: v }) },
        },
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
            bet: (index) => "bet-" + index,
            discard: (index) => "hand-" + index,
            pass: () => "pass",
            chooseNextPlayer: (index) => "choosePlayer-" + index,
            choose: (index) => "choose-" + index
        },
        component: Skull
    },
    "LiarsDice": {
        displayName: "Liar's Dice",
        dsl: dsl(g => g.net.zomis.games.impl.LiarsDiceGame.game),
        actions: {
            bet: (amount) => ({
                key: 'amount-' + amount,
                next: (face) => `bet ${amount}x ${face}'s`
            }),
            liar: () => "liar",
            spotOn: () => "spotOn"
        },
        component: LiarsDice
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
        component: DungeonMayhem
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
        actions: tttActions,
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
    storeModules() {
        let modules = {}
        for (let key of enabledGames()) {
            let game = supportedGames[key]
            if (game.dsl) {
                modules["DslGameState"] = DslGameState
            } else if (game.store) {
                modules[key] = game.store
            }
        }
        return modules
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
    stateModules(state) {
        let modules = this.storeModules()
        return Object.keys(modules).map(moduleName => state[moduleName]);
    },
    enabledGameKeys() {
        return enabledGames()
    },
    games: supportedGames
}
