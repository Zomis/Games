<template>
    <v-col class="player-in-game-info">
        <template v-if="displayStyle === 'avatars' || displayStyle === 'vs'">
            <v-tooltip bottom>
                <template v-slot:activator="{ on }">
                    <v-avatar :size="48" v-on="on" :class="[winResultClass, eliminatedOpacityClass]">
                        <img
                            :src="player.picture"
                            :alt="player.name" />
                    </v-avatar>
                </template>
                <span>{{ tooltip }}</span>
            </v-tooltip>
        </template>
        <template v-if="displayStyle == 'vs'">
            <span>{{ player.name }}</span>
            <span v-if="elim">({{ elimResult }})</span>
        </template>
        <template v-if="displayStyle == 'table'">
            <div>
                <span>{{ player.name }}</span>
                <span>{{ elimResult }}</span>
                <span>{{ elimPosition }}</span>
            </div>
        </template>
    </v-col>
</template>
<script>
function winResultValue(winResult) {
    if (winResult === 'WIN') return 1
    if (winResult === 'LOSS') return -1
    if (winResult === 'DRAW') return 0
    throw winResult
}

export default {
    name: "PlayerInGameInfo",
    props: ["player", "view", "eliminations", "displayStyle"],
    computed: {
        winResultClass() {
            if (!this.elim) return 'not-eliminated';
            let winValue = this.elim.winResult
            if (winValue < 0) return 'loser'
            if (winValue > 0) return 'winner'
            return 'draw'
        },
        eliminatedOpacityClass() {
            if (!this.elim) return 'normal'
            if (this.elim.winResult > 0) return 'normal'
            return 'eliminated'
        },
        tooltip() {
            return this.player.name
        },
        myTurn() {
            return this.view.currentPlayer === this.player.index
        },
        score() {
            if (!this.view.scores) return null;
            return this.view.scores[this.player.index]
        },
        elimResult() {
            if (!this.elim) return ''
            let winValue = this.elim.winResult
            if (winValue < 0) return 'LOSE'
            if (winValue > 0) return 'WIN'
            return 'DRAW'
        },
        elimPosition() {
            if (!this.elim) return ''
            return this.elim.position
        },
        elim() {
            let elimination = this.elimination
            if (!elimination) return null
            let server = !!elimination.type
            if (server) {
                return {
                    position: elimination.position,
                    winResult: winResultValue(elimination.winResult)
                }
            }
            return {
                position: elimination.position,
                winResult: elimination.winResult.result
            }
        },
        elimination() {
            return this.eliminations.find(elim => elim.playerIndex === this.player.index || elim.player === this.player.index)
        }
    }
}
</script>
<style scoped>
.player-in-game-info {
    display: inline;
}

.normal {
    opacity: 1;
}

.eliminated {
    opacity: 0.3;
}

.loser {
    box-shadow: 5px 5px 10px 0px rgba(255, 0, 0, 0.75);
}

.draw {
    box-shadow: 5px 5px 10px 0px rgba(255, 255, 0, 0.75);
}

.winner {
    box-shadow: 5px 5px 10px 0px rgba(0, 255, 0, 0.75);
}
</style>