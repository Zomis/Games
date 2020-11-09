<template>
  <v-list-item :key="player.id">
    <v-list-item-content>
      <v-list-item-title>
        <PlayerProfile
          :player="player"
          show-name
        />
      </v-list-item-title>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn
        v-if="inviteable && controllable"
        color="info"
        @click="sendInvite()"
      >
        <v-icon>mdi-account-plus</v-icon>
      </v-btn>
      <template v-for="(state, index) in playerState">
        <v-btn
          v-if="state === 'accepted'"
          :key="index"
          color="green"
        >
          <v-icon>mdi-check</v-icon>
        </v-btn>
        <v-btn
          v-if="state === 'invited'"
          :key="index"
          color="orange"
        >
          <v-icon>mdi-timer-sand</v-icon>
        </v-btn>
        <!-- TODO: Show declined invites but allow to invite again <v-btn :key="index" v-if="state === false" color="error">x</v-btn> -->
        <!-- TODO: Allow kicking/removing people from an invite -->
      </template>
      <!--
          Declined: Red 'X' button
          Accepted: Green checkmark
          Pending: Yellow '?'

          Invitable: Blue '+'
      -->
    </v-list-item-action>
  </v-list-item>
</template>
<script>
import Socket from "@/socket";
import PlayerProfile from "@/components/games/common/PlayerProfile"
import { mapState } from 'vuex';

export default {
    name: "InvitePlayer",
    props: ["invite", "player", "controllable"],
    components: { PlayerProfile },
    data() {
        return {}
    },
    methods: {
        sendInvite() {
            Socket.route(`invites/${this.invite.inviteId}/send`, { invite: [this.player.id] });
        }
    },
    computed: {
        ...mapState('lobby', {
            yourPlayer: state => state.yourPlayer
        }),
        isAI() {
            return this.player.name.startsWith("#AI_");
        },
        totalPlayers() {
            return this.invite.players.length + this.invite.invited.length
        },
        inviteIsFull() {
            if (!this.invite) return false
            return this.totalPlayers === this.invite.maxPlayers
        },
        inviteable() {
            if (this.inviteIsFull) return false
            if (this.player.id === this.yourPlayer.id) { return false }
            return this.isAI || this.playerState.length === 0
        },
        playerState() {
            if (!this.invite) { return [] }
            return [...this.invite.players.filter(e => e.id === this.player.id).map(() => "accepted"),
                    ...this.invite.invited.filter(e => e.id === this.player.id).map(() => "invited")]
        }
    }
}
</script>