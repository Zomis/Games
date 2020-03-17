<template>
  <v-stepper v-model="step">
    <v-stepper-header>
      <v-stepper-step :complete="step > 1" step="1">Game Options</v-stepper-step>
      <v-divider></v-divider>
      <v-stepper-step step="2">Send Invites</v-stepper-step>
    </v-stepper-header>

    <v-stepper-items>
      <v-stepper-content step="1">
        <v-card
          class="invite-step mb-12"
          color="grey lighten-1"
        >
          <v-switch v-model="useDatabase" label="Use Database" disabled />
          <v-switch v-model="allowAnyoneJoin" label="Allow anyone to join" disabled />
          <v-radio-group v-model="playerOrder" disabled>
            <v-radio
              v-for="n in ['Ordered', 'Random']"
              :key="n"
              :label="n"
              :value="n"
            ></v-radio>
          </v-radio-group>
          <v-select
            v-model="timeLimit"
            :items="timeLimitOptions"
            label="Time Limit"
          ></v-select>
          <!-- Game-specific options -->
        </v-card>

        <v-btn
          color="primary"
          @click="startInvite()"
        >
          Continue
        </v-btn>

        <v-btn text>Cancel</v-btn>
      </v-stepper-content>

      <v-stepper-content step="2">
        <!-- Share invite link -->
        <v-card
          class="invite-step mb-12"
          color="grey lighten-1"
        >
          <v-row>
            <v-col cols="12">
              <v-list light>
                <template v-for="(player, index) in users">
                  <v-divider :key="`divider-${player.id}`" v-if="index > 0"></v-divider>
                  <InvitePlayer :key="`player-${player.id}`" :invite="invite" :player="player" />
                </template>
              </v-list>
            </v-col>
          </v-row>
        </v-card>

        <v-btn
          color="primary"
          @click="startGame()"
          :disabled="!gameStartable"
        >
          Start Game
        </v-btn>

        <v-btn text @click="cancelInvite()">Cancel</v-btn>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
  <!--
    Simple flow: Click on a button, do step 1 and half 2
    Can find out what playerCount is by starting to set up a local game. But sending playerCount from server might also be good

    Complex flow: Start invite
    Step 1a: Generic options (Time limit, player order, save in database)
    Step 1b: Choose options (If applicable)
    Step 2: Invite people - share link or send invites (or click "Public game", and have Invite Link)
      Or just let people join (like most games do)
    Step 3: Start game
  -->
</template>
<script>
import { mapState } from 'vuex';
import Socket from "@/socket";
import InvitePlayer from "@/components/lobby/InvitePlayer"

export default {
  data() {
    return {
      useDatabase: true,
      allowAnyoneJoin: false,
      timeLimit: 'No Limit',
      timeLimitOptions: ['No Limit'],
      playerOrder: 'Ordered'
    }
  },
  components: {
    InvitePlayer
  },
  methods: {
    cancelInvite() {
      this.$store.dispatch("lobby/cancelInvite")
    },
    startInvite() {
      this.$store.dispatch("lobby/createServerInvite")
    },
    startGame() {
      Socket.route(`invites/${this.inviteId}/start`);
    }
  },
  computed: {
    gameStartable() {
      if (!this.invite) return false;
      let playerCount = this.invite.invitesSent.filter(e => e.status).length;
      return playerCount >= this.invite.playersMin
    },
    ...mapState('lobby', {
      loginName: state => state.yourPlayer.name,
      step: state => state.inviteWaiting.inviteStep,
      inviteId: state => state.inviteWaiting ? state.inviteWaiting.inviteId : null,
      invite(state) {
        if (this.inviteId == null) return null
        return state.inviteWaiting
      },
      gameType: state => state.inviteWaiting.gameType,
      users(state) {
        return state.lobby[state.inviteWaiting.gameType] || []
      }
    })
  }
}
</script>
<style scoped>
.invite-step {
  max-height: 500px;
}
</style>