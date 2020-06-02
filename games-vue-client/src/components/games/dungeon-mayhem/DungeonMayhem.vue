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
                    <Actionable button :actionable="`target:player-${playerIndex};shield-null;discarded-null`" actionType="target" :actions="actions">
                        Target
                    </Actionable>
                    <p>Hand:</p>
                    <div v-if="player.hand[0]">
                        <CardZone>
                            <DungeonMayhemCard v-for="(card, index) in player.hand" :key="index"
                                :card="card" :icons="icons" :actionable="'play-' + card.name" :actions="actions" />
                        </CardZone>
                    </div>
                    <CardZone v-else>
                        <v-icon v-for="index in player.hand" :key="index">mdi-crosshairs-question</v-icon>
                    </CardZone>

                    <v-menu>
                        <template v-slot:activator="{ on }">
                            <v-btn v-on="on">
                                {{ player.discard.length }} Discarded
                            </v-btn>
                        </template>
                        <CardZone>
                            <DungeonMayhemCard v-for="(card, index) in player.discard" :key="index"
                                :card="card" :icons="icons" :actions="actions" :actionable="`target:player-${playerIndex};shield-null;discarded-${index}`" />
                        </CardZone>
                    </v-menu>

                    <p>Played:</p>
                    <CardZone>
                        <DungeonMayhemCard v-for="(card, index) in player.played" :key="index" :card="card" :icons="icons" :actions="actions" />
                    </CardZone>
                    <p>Shields:</p>
                    <CardZone>
                        <DungeonMayhemCard v-for="(card, index) in player.shields" :key="index" :card="card" :icons="icons"
                         :actions="actions" :actionable="`target:player-${playerIndex};shield-${index};discarded-null`" />
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
import GameTreeView from "@/components/games/debug/GameTreeView"
import CardZone from "@/components/games/common/CardZone"
import DungeonMayhemCard from "./DungeonMayhemCard"
import Actionable from "@/components/games/common/Actionable"

export default {
    name: "DungeonMayhem",
    props: ["view", "actions", "players"],
    data() {
        return {
            icons: {
                ATTACK: 'mdi-sword',
                PLAY_AGAIN: 'mdi-flash',
                HEAL: 'mdi-heart',
                DRAW: 'mdi-plus-box-multiple',
                SHIELD: 'mdi-shield',
                FIREBALL: 'mdi-fire',
                STEAL_SHIELD: 'mdi-shield-home',
                SWAP_HITPOINTS: 'mdi-rotate-3d-variant',
                PICK_UP_CARD: 'mdi-delete-restore',
                DESTROY_ALL_SHIELDS: 'mdi-shield-off',
                PROTECTION_ONE_TURN: 'mdi-account-lock',
                DESTROY_SINGLE_SHIELD: 'mdi-shield-half-full',
                STEAL_CARD: 'mdi-credit-card-scan',
                HEAL_AND_ATTACK_FOR_EACH_OPPONENT: 'mdi-hexagram-outline',
                ALL_DISCARD_AND_DRAW: 'mdi-account-box-multiple'
            }
        }
    },
    components: {
        PlayerProfile,
        GameTreeView,
        Actionable,
        CardZone,
        DungeonMayhemCard
    }
}
</script>