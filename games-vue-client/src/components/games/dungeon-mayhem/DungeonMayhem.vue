<template>
    <v-container fluid>
        <v-row>
            <v-col v-for="(player, playerIndex) in view.players" :key="playerIndex">
                <v-card :class="'color-' + player.color">
                    <PlayerProfile :player="players[playerIndex]" show-name />
                    <p>Health: {{ player.health }}</p>
                    <p>Deck: {{ player.deck }}</p>
                    <Actionable button :actionable="`target:player-${playerIndex};shield-null;discarded-null`" actionType="target" :actions="actions">
                        Target
                    </Actionable>
                    <p>Hand:</p>
                    <div v-if="player.hand[0]">
                        <CardZone>
                            <DungeonMayhemCard v-for="(card, index) in player.hand" :key="index"
                                :card="card" class="list-complete-item" :actionable="'play-' + card.name" :actions="actions" />
                        </CardZone>
                    </div>
                    <CardZone v-else>
                        <v-icon v-for="index in player.hand" class="list-complete-item" :key="index">mdi-crosshairs-question</v-icon>
                    </CardZone>

                    <v-menu>
                        <template v-slot:activator="{ on }">
                            <v-btn v-on="on">
                                {{ player.discard.length }} Discarded
                            </v-btn>
                        </template>
                        <CardZone>
                            <DungeonMayhemCard v-for="(card, index) in player.discard" :key="index"
                                :card="card" class="list-complete-item" :actions="actions" :actionable="`target:player-${playerIndex};shield-null;discarded-${index}`" />
                        </CardZone>
                    </v-menu>

                    <p>Played:</p>
                    <CardZone>
                        <DungeonMayhemCard class="list-complete-item" v-for="(card, index) in player.played" :key="index" :card="card" :actions="actions" />
                    </CardZone>
                    <p>Shields:</p>
                    <CardZone>
                        <DungeonMayhemCard v-for="(card, index) in player.shields" :key="index" :card="card"
                         :actions="actions" class="list-complete-item" :actionable="`target:player-${playerIndex};shield-${index};discarded-null`" />
                    </CardZone>
                </v-card>
            </v-col>
        </v-row>
        <v-row>
            <v-col>
                <div v-for="(item, index) in view.stack" :key="index">{{ item }}</div>
            </v-col>
        </v-row>
    </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CardZone from "@/components/games/common/CardZone"
import DungeonMayhemCard from "./DungeonMayhemCard"
import Actionable from "@/components/games/common/Actionable"

export default {
    name: "DungeonMayhem",
    props: ["view", "actions", "players"],
    components: {
        PlayerProfile,
        Actionable,
        CardZone,
        DungeonMayhemCard
    }
}
</script>
<style scoped>
@import "../../../assets/games-animations.css";

.actionable {
    border-style: solid !important;
    border-width: thick !important;
    border-color: #ffd166 !important;
}
</style>