<template>
    <v-container fluid>
        <h3 v-if="invite">{{ invite.host.name }} is preparing a game of {{ invite.gameType }}</h3>
        <v-card
          class="invite-step mb-12"
          color="grey lighten-1"
          v-if="invite"
        >
            <v-card-text>
                <v-row>
                    <v-col cols="12">
                        <v-list light>
                            <template v-for="(player, index) in users">
                                <v-divider :key="`divider-${player.id}`" v-if="index > 0"></v-divider>
                                <InvitePlayer :key="`player-${player.id}`" :invite="invite" :player="player" :controllable="isHost" />
                            </template>
                            <template v-if="isHost">
                                <v-divider :key="`divider-${invite.host.id}`" />
                                <InvitePlayer :key="`player-${invite.host.id}`" :invite="invite" :player="invite.host" :controllable="isHost" />
                            </template>
                            <template v-if="!isHost">
                                <v-divider :key="`divider-${yourPlayer.id}`" />
                                <InvitePlayer :key="`player-${yourPlayer.id}`" :invite="invite" :player="yourPlayer" :controllable="isHost" />
                            </template>
                        </v-list>
                    </v-col>
                </v-row>
            </v-card-text>
            <v-card-actions v-if="isHost">
                <v-btn
                  color="primary"
                  @click="startInvite()" :disabled="!gameStartable">Start Game</v-btn>
                <v-btn @click="cancelInvite()" color="error">Cancel</v-btn>
            </v-card-actions>
            <v-card-actions v-if="!isInGame">
                <v-btn
                  color="primary"
                  @click="joinInvite()">Join Game</v-btn>
                <v-btn @click="declineInvite()" color="error">Decline</v-btn>
            </v-card-actions>
        </v-card>
    </v-container>
</template>
<script>
import { mapState } from "vuex"
import InvitePlayer from "@/components/lobby/InvitePlayer"
import Socket from "@/socket"

export default {
    name: "InviteScreen",
    props: ["inviteId"],
    components: { InvitePlayer },
    mounted() {
        console.log("InviteScreen mounted")
        if (this.users.length === 0 && Socket.isConnected()) {
            this.$store.dispatch("lobby/joinAndList");
        }
        this.$store.dispatch('wall').then(() => {
            this.$store.dispatch("lobby/inviteView", { inviteId: this.inviteId })
        })
    },
    methods: {
        joinInvite() {
            Socket.route(`invites/${this.inviteId}/respond`, { accepted: true });
        },
        declineInvite() {
            Socket.route(`invites/${this.inviteId}/respond`, { accepted: false });
            this.$router.push("/")
        },
        cancelInvite() {
            Socket.route(`invites/${this.inviteId}/cancel`);
            this.$router.push("/")
        },
        startInvite() {
            Socket.route(`invites/${this.inviteId}/start`);
        }
    },
    computed: {
        gameStartable() {
            if (!this.invite) return false;
            let playerCount = this.invite.players.length;
            return playerCount >= this.invite.minPlayers && playerCount <= this.invite.maxPlayers;
        },
        ...mapState("lobby", {
            invite(state) {
                return state.inviteViews[this.inviteId]//.find(inv => inv.inviteId === this.inviteId);
            },
            yourPlayer(state) {
                return { id: state.yourPlayer.playerId, name: state.yourPlayer.name, picture: "TODO-UNKNOWN" }
            },
            myPlayerId(state) {
                return state.yourPlayer.playerId
            },
            isHost(state) {
                if (!this.invite) return false;
                return this.invite.host.id === state.yourPlayer.playerId
            },
            users(state) {
                if (!this.invite) return [];
                return state.lobby[this.invite.gameType] || []
            }
        }),
        isInGame() {
            if (!this.invite) return false;
            return this.invite.players.findIndex(player => player.id === this.myPlayerId) >= 0
        }
    }
}
</script>
