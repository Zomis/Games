<template>
    <v-container fluid>
        <v-row>
            <v-col v-for="mission in view.missions" :key="mission.missionNumber">
                <p>Mission #{{ mission.missionNumber }} - {{ mission.teamSize }} people</p>
                <p v-if="mission.result !== null">
                    <v-icon color="green" v-if="mission.result">mdi-check-circle</v-icon>
                    <v-icon color="red" v-else>mdi-close-circle</v-icon>
                    <span>{{ mission.failsGotten }} fails</span>
                </p>
                <p v-if="mission.failsNeeded != 1">{{ mission.failsNeeded }} fails needed</p>
                <p>
                    <v-icon color="purple" v-if="view.votingTeam && view.votingTeam.missionNumber == mission.missionNumber">mdi-helicopter</v-icon>
                </p>
            </v-col>
        </v-row>
        <v-row>
            <v-col>
                Rejected Teams: {{ view.rejectedTeams }}
            </v-col>
        </v-row>
        <v-row>
            <v-col cols="2" v-for="(player, playerIndex) in view.players" :key="playerIndex">
                <v-card>
                    <v-card-title>
                        <PlayerProfile show-name :player="context.players[playerIndex]" />
                    </v-card-title>
                    <v-card-text>
                        <v-icon color="orange" v-if="player.leader">mdi-crown</v-icon>
                        <v-icon v-if="player.thumb">mdi-eye-circle</v-icon>
                        <v-icon v-if="player.ladyOfTheLakeImmunity">mdi-face-woman-outline</v-icon>
                        <v-icon color="blue" v-if="player.ladyOfTheLakePlayer">mdi-face-woman</v-icon>
                        <v-icon color="purple" v-if="player.inTeam">mdi-helicopter</v-icon>
                        <p v-if="player.vote !== null">
                            <v-icon color="green" v-if="player.vote">mdi-check-circle</v-icon>
                            <v-icon color="red" v-else>mdi-close-circle</v-icon>
                        </p>
                        <div v-if="player.character">
                            <p>{{ player.character }}</p>
                            <p>{{ characters[player.character] }}</p>
                        </div>
                    </v-card-text>
                </v-card>
            </v-col>
        </v-row>
        <v-row>
            <v-col>
                <template v-if="view.currentMission">
                    Current Mission: {{ view.currentMission }}
                </template>
            </v-col>
        </v-row>
        <v-row>
            <v-col>
                <Actionable button :actionType="['teamChoice']" :actions="actions" stickyMenu>Choose team</Actionable>
                <Actionable button :actionType="['vote']" :actions="actions">Vote for team</Actionable>
                <Actionable button :actionType="['performMission']" :actions="actions">Perform mission</Actionable>
                <Actionable button :actionType="['assassinate']" :actions="actions" stickyMenu>Assassinate</Actionable>
                <Actionable button :actionType="['useLadyOfTheLake']" :actions="actions">Lady of the lake</Actionable>
            </v-col>
        </v-row>
        <v-row>
            <v-card max-width="450" class="mx-auto">
                <v-toolbar color="cyan" dark>
                    <v-toolbar-title>Characters</v-toolbar-title>
                </v-toolbar>
                <v-list three-line>
                    <template v-for="(character, index) in charactersInGame">
                        <v-list-item :key="'item-' + index">
                            <v-list-item-content>
                                <v-list-item-title v-html="character.name" />
                                <v-list-item-subtitle v-html="character.description" />
                            </v-list-item-content>
                        </v-list-item>
                        <v-divider :key="'divider-' + index" />
                </template>
                </v-list>
            </v-card>
        </v-row>
    </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"

export default {
    name: "Avalon",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, Actionable
    },
    computed: {
        characters() {
            let oberonMessage = " Sees other evil except Oberon."
            return {
                MERLIN: "Loyal servant of King Arthur. Knows evil except for Mordred.",
                MORDRED: "Master of evil. Hides from Merlin" + oberonMessage,
                PERCIVAL: "Loyal servant of King Arthur. Sees Merlin and Morgana.",
                MORGANA: "Evil. Is seen by Percival." + oberonMessage,
                ASSASSIN: "Evil. Should figure out who Merlin is and kill him at the end." + oberonMessage,
                MINION_OF_MORDRED: "Evil. Sees other evil except Oberyn." + oberonMessage,
                OBERON: "Evil. Does not know the other evil.",
                LOYAL_SERVANT_OF_KING_ARTHUR: "Loyal servant of King Arthur. Knows nothing."
            }
        },
        charactersInGame() {
            if (!this.view || !this.view.characters) return []
            let displayCharacters = this.view.characters.map(c => ({
                name: c,
                description: this.characters[c]
            }));
            return displayCharacters
        }
    }
}
</script>
