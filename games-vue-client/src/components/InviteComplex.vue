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
          class="mb-12"
          color="grey lighten-1"
          height="500px"
        >
        <!--
          Use Database
          Game Timeout
          Game Specific options
        -->
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
        <v-card
          class="mb-12"
          color="grey lighten-1"
          height="500px"
        >
          <v-row>
            <v-col cols="12">
              <v-list light>
                <template v-for="(name, index) in users">
                  <v-divider :key="`divider-${index}`" v-if="index > 0"></v-divider>
                  <v-list-item :key="index">
                    <v-list-item-content>
                      <v-list-item-title v-html="name"></v-list-item-title>
                    </v-list-item-content>
                    <v-list-item-action>
                      <v-btn color="info" @click="sendInvite(name)" v-if="name !== loginName">Invite</v-btn>
                    </v-list-item-action>
                  </v-list-item>
                </template>
              </v-list>
            </v-col>
          </v-row>
        </v-card>

        <v-btn
          color="primary"
          @click="startGame()"
        >
          Start Game
        </v-btn>

        <v-btn text>Cancel</v-btn>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
  <!--
    Simple flow: Click on a button, go!
    configure in supportedGames.js which games allow this? Or send playerCount from server for all games?

    Complex flow: Start invite
    Step 1: Choose options
    Step 2: Invite people - share link or send invites
      Or just let people join (like most games do)
      Player options, all check "Ready"
    Step 3: Start game
  -->
</template>
<script>
import { mapState } from 'vuex';
import Socket from "@/socket";

export default {
  data() {
    return {
      step: 1
    }
  },
  methods: {
    sendInvite(name) {
      Socket.route(`invites/${this.inviteId}/send`, { invite: [name] });
    },
    startInvite() {
      this.step = 2
    },
    startGame() {
      Socket.route(`invites/${this.inviteId}/start`);
    }
  },
  computed: {
    ...mapState(['loginName']),
    ...mapState('lobby', {
      inviteId: state => state.complexInvite.inviteWaiting ? state.complexInvite.inviteWaiting.inviteId : null,
      gameType: state => state.complexInvite.gameType,
      users: state => state.lobby[state.complexInvite.gameType]
    })
  }
}
</script>
