<template>
  <v-container fluid>
    <v-layout column fill-height>
      <v-layout row wrap justify-center align-center>
        <v-flex xs1 text-xs-center>{{ position }} / {{ maxReplayStep }}</v-flex>
        <v-flex xs10>
          <v-slider v-model="position" :max="maxReplayStep" :min="0"></v-slider>
        </v-flex>
      </v-layout>
      <v-layout row justify-center align-center>
        <v-btn @click="setPosition(0)">
          <v-icon>mdi-skip-previous</v-icon>
        </v-btn>
        <v-btn @click="changePosition(-1)">
          <v-icon>mdi-rewind</v-icon>
        </v-btn>
        <v-btn v-if="!running" @click="setRunning(true)">
          <v-icon>mdi-play</v-icon>
        </v-btn>
        <v-btn v-else @click="setRunning(false)">
          <v-icon>mdi-pause</v-icon>
        </v-btn>
        <v-btn @click="changePosition(1)">
          <v-icon>mdi-fast-forward</v-icon>
        </v-btn>
        <v-btn @click="setPosition(maxReplayStep)">
          <v-icon>mdi-skip-next</v-icon>
        </v-btn>
      </v-layout>
      <v-flex grow>
        <GameHead xs12 :gameInfo="gameInfo" :view="currentView" :eliminations="currentEliminations" v-if="gameInfo"></GameHead>
        <div xs12>
            Started at {{ timeStarted }} - Ended at {{ timeLastAction }}
        </div>
        <component xs12 :is="gameComponent" :actions="{}" :view="currentView" :players="players" />
        <GameResult xs12 :gameInfo="gameInfo"></GameResult>
      </v-flex>
    </v-layout>
    <AiQuery :gameInfo="gameInfo" :gamePosition="position" />
  </v-container>
</template>
<script>
import supportedGames from "@/supportedGames"
import axios from "axios";
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";
import AiQuery from "./AiQuery";

function unixtimeToString(date) {
    return new Date(date * 1000).toISOString().replace("T"," ").replace(/\.\d+Z/g,"")
}

export default {
    name: "GameReplay",
    props: ["gameUUID"],
    data() {
        return {
            running: false,
            timer: null,
            position: 0,
            replay: null
        }
    },
    components: {
        GameHead,
        GameResult,
        AiQuery,
        ...supportedGames.components() // TODO: This might not be needed
    },
    created() {
        axios.get(`${this.baseURL}games/${this.gameUUID}/replay`).then(response => {
            console.log(response)
            this.replay = response.data
            this.setRunning(true)
        })
    },
    methods: {
        setPosition(position) {
            this.setRunning(false);
            this.position = position;
        },
        changePosition(offset) {
            this.setRunning(false);
            this.position = this.position + offset;
        },
        setRunning(running) {
            this.running = running;
            if (running) {
                this.timer = setInterval(() => {
                    this.position = this.position + 1;
                    if (this.position === this.replayLength) {
                        this.setRunning(false);
                    }
                }, 750);
            } else {
                clearInterval(this.timer);
            }
        }
    },
    computed: {
        baseURL() {
            return process.env.VUE_APP_URL
        },
        players() {
            if (this.gameInfo == null) return [];
            return this.gameInfo.players;
        },
        timeStarted() {
            if (this.replay == null) return "";
            return unixtimeToString(this.replay.timeStarted)
        },
        timeLastAction() {
            if (this.replay == null) return "";
            return unixtimeToString(this.replay.timeLastAction)
        },
        maxReplayStep() {
            if (this.replay == null) { return 0 }
            return this.replay.views.length - 1
        },
        gameComponent() {
            if (this.replay == null) { return null }
            return supportedGames.games[this.replay.gameType].component
        },
        currentEliminations() {
            return [] // TODO: Determine when eliminations were done and add dynamically here based on position.
        },
        playerNames() {
            if (this.replay == null) { return [] }
            return this.replay.playersInGame.map(pig => pig.player.name)
        },
        currentView() {
            if (this.replay == null) { return null }
            return this.replay.views[this.position]
        },
        gameInfo() {
            if (this.replay == null) { return null }
            let sortedPlayersInGame = this.replay.playersInGame.slice()
            sortedPlayersInGame.sort((a, b) => a.playerIndex - b.playerIndex)
            return {
                gameType: this.replay.gameType,
                players: sortedPlayersInGame.map(pig => pig.player),
                gameId: this.gameUUID,
                yourIndex: -1
            }
        }
    }
}
</script>