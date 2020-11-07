<template>
    <v-container fluid>
        <v-row>
            <v-col cols="2" v-for="(player, playerIndex) in view.players" :key="playerIndex">
                <v-card>
                    <v-card-title>
                        <PlayerProfile show-name :player="context.players[playerIndex]" />
                    </v-card-title>
                    <v-card-text>
                        <CardZone>
                            <Actionable v-for="(card, cardIndex) in player.influence" :actions="actions" :key="cardIndex" :actionable="card">
                                <v-card>
                                    <v-card-title>
                                        <div>
                                            {{ card }}
                                        </div>
                                    </v-card-title>
                                </v-card>
                            </Actionable>
                        </CardZone>
                        <div>
                            <Actionable v-if="actions.available[`players/${playerIndex}`]" button :actionable="`players/${playerIndex}`" :actions="actions">
                                {{ actions.available[`players/${playerIndex}`].actionType }}
                            </Actionable>
                        </div>
                        <div>
                            <p>{{ player.coins }}</p>
                        </div>
                    </v-card-text>
                </v-card>
            </v-col>
        </v-row>
        <v-row>
            <v-col>
                <p v-for="(task, taskIndex) in view.stack" :key="taskIndex">
                    {{ task }}
                </p>
            </v-col>
        </v-row>
        <v-row>
            <v-col>
                <Actionable button :actionType="['perform']" :actions="actions">Perform action</Actionable>
                <Actionable button :actionType="['approve']" :actions="actions">Approve</Actionable>
                <Actionable button :actionType="['challenge']" :actions="actions">Challenge</Actionable>
                <Actionable button :actionType="['counteract']" :actions="actions">Counteract</Actionable>
                <Actionable button :actionType="['reveal']" :actions="actions">Reveal</Actionable>
                <Actionable button :actionType="['putBack']" :actions="actions">Put back</Actionable>
                <Actionable button :actionType="['lose']" :actions="actions">Lose</Actionable>
            </v-col>
        </v-row>
    </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"
import CardZone from "@/components/games/common/CardZone"

export default {
    name: "Coup",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, Actionable, CardZone
    },
}
</script>
