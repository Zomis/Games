<template>
    <v-container fluid>
        <GameTreeView :view="view" />
        <GameTreeView :actions="actions" />
        <v-row>
            <v-col v-for="(player, playerIndex) in view.players" :key="playerIndex">
                <v-card :class="'color-' + player.color">
                    <PlayerProfile :player="players[playerIndex]" show-name />
                    <p>Health: {{ player.health }}</p>
                    <p>Deck: {{ player.deck }}</p>
                    <v-btn :disabled="!actions.available[`target:player-${playerIndex};shield-null;discarded-null`]"
                      @click="actions.perform('target', `target:player-${playerIndex};shield-null;discarded-null`)">
                        Target
                    </v-btn>
                    <div v-if="player.hand[0]">
                        <CardZone>
                            <DungeonMayhemCard v-for="(card, index) in player.hand" :key="index" :card="card" actionType="play" :action="'play-' + card.name" :actions="actions" />
                        </CardZone>
                    </div>
                    <p v-else>Hand: {{ player.hand }}</p>
                    <p>
                        Discarded: {{ player.discard.length }}
                        <v-btn v-for="(card, index) in player.discard" :key="index"
                             :disabled="!actions.available[`target:player-${playerIndex};shield-null;discarded-${index}`]"
                         @click="actions.perform('target', `target:player-${playerIndex};shield-null;discarded-${index}`)">
                            Target
                        </v-btn>
                    </p>
                    <p>Played:</p>
                    <CardZone>
                        <DungeonMayhemCard v-for="(card, index) in player.played" :key="index" :card="card" />
                    </CardZone>
                    <p>Shields:</p>
                    <CardZone>
                        <DungeonMayhemCard v-for="(card, index) in player.shields" :key="index" :card="card"
                         :actions="actions" actionType="target" :action="`target:player-${playerIndex};shield-${index};discarded-null`" />
                    </CardZone>
                </v-card>
            </v-col>
        </v-row>
        <v-row>
            <div v-for="(item, index) in view.stack" :key="index">{{ item }}</div>
        </v-row>
    </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import GameTreeView from "@/components/games/debug/GameTreeView"
import CardZone from "@/components/games/common/CardZone"
import DungeonMayhemCard from "./DungeonMayhemCard"

export default {
    name: "DungeonMayhem",
    props: ["view", "actions", "players"],
    components: {
        PlayerProfile,
        GameTreeView,
        CardZone,
        DungeonMayhemCard
    }
}
</script>