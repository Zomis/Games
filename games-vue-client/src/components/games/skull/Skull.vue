<template>
    <v-container fluid>
        <v-row>
            <v-col v-for="(player, playerIndex) in view.players" :key="playerIndex">
                <v-card class="player" :class="{ passed: player.pass, 'current-player': playerIndex == view.currentPlayer }">
                    <v-card-title>
                        <PlayerProfile show-name :player="context.players[playerIndex]" />
                    </v-card-title>
                    <v-card-text>
                        <CardZone v-if="Array.isArray(player.hand)">
                            <Actionable button v-for="(card, index) in player.hand" :key="index"
                                :actions="actions" class="list-complete-item" :actionable="'hand-' + card">
                                <v-icon>{{ icons[card] }}</v-icon>
                            </Actionable>
                        </CardZone>
                        <CardZone v-else>
                            <Actionable button v-for="(index) in player.hand" :key="index"
                                :actions="actions" class="list-complete-item" :actionable="'choosePlayer-' + playerIndex">
                                <v-icon>mdi-crosshairs-question</v-icon>
                            </Actionable>
                        </CardZone>

                        <div>Played</div>
                        <CardZone v-if="Array.isArray(player.board)">
                            <Actionable button v-for="(card, index) in player.board" :key="index"
                                :actions="actions" class="list-complete-item" :actionable="'choose-' + playerIndex">
                                <v-icon>{{ icons[card] }}</v-icon>
                            </Actionable>
                        </CardZone>
                        <span v-else>
                            <CardZone>
                                <Actionable button v-for="index in player.board" :actionable="'choose-' + playerIndex" :key="index"
                                  class="list-complete-item" :actions="actions">
                                    <v-icon>mdi-crosshairs-question</v-icon>
                                </Actionable>
                            </CardZone>
                        </span>

                        <div>Chosen</div>
                        <CardZone>
                            <v-icon v-for="(card, index) in player.chosen" :key="index"
                                class="list-complete-item">{{ icons[card] }}</v-icon>
                        </CardZone>

                        <div>Bet</div>
                        <div>{{ player.bet }}</div>
                        <Actionable button v-if="context.players[playerIndex].controllable" :actionType="['pass', 'bet']" :actions="actions">Bet/Pass</Actionable>

                        <div>Points</div>
                        <span>{{ player.points }}</span>
                    </v-card-text>
                </v-card>
            </v-col>
        </v-row>
</v-container>
</template>
<script>
import CardZone from "@/components/games/common/CardZone"
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"

export default {
    name: "Skull",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, CardZone,
        Actionable
    },
    data() {
        return {
            icons: { FLOWER: 'mdi-flower', SKULL: 'mdi-skull' }
        }
    }
}
</script>
<style scoped>
@import "../../../assets/games-animations.css";

.v-card.passed {
    opacity: 0.5
}

.actionable {
    border-style: solid !important;
    border-width: thick !important;
    border-color: #ffd166 !important;
}

.player.current-player {
    border: 1px solid yellow !important;
    box-shadow: 0px 0px 5px 6px yellow !important;
}
</style>
