<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(player, playerIndex) in view.players"
        :key="playerIndex"
      >
        <v-card>
          <v-card-title>
            <PlayerProfile
              :size="32"
              :context="context"
              :player-index="playerIndex"
              show-name
            />
          </v-card-title>
          <v-card-text>
            <span>
              {{ player.points }} points
            </span>
            <v-icon
              v-if="player.placed"
              color="green"
            >
              mdi-image
            </v-icon>
            <v-icon
              v-if="player.voted"
              color="green"
            >
              mdi-check-circle
            </v-icon>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <p>{{ view.phase }}</p>
        <p>Storyteller</p>
        <PlayerProfile
          :size="32"
          :context="context"
          :player-index="view.storyteller"
          show-name
        />
      </v-col>
    </v-row>
    <v-row v-if="view.story">
      <v-col>
        <v-card>
          <v-card-title>
            Story
          </v-card-title>
          <v-card-text>
            <span style="font-style: italic">{{ view.story }}</span>
            <CardZone>
              <v-img
                v-for="card in view.board"
                :key="card"
                class="animate"
                :src="`https://zomis-games-cdn.s3.eu-central-1.amazonaws.com/games/dixit/${view.config.cardSet}/${card}`"
                @click="vote(card)"
              />
            </CardZone>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row v-if="view.action === 'story'">
      <v-col>
        <v-text-field
          v-model="story"
          label="Write your story and then click on a card in your hand below"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-card>
          <v-card-title>Your hand</v-card-title>
          <v-card-text>
            <CardZone>
              <v-img
                v-for="card in view.hand"
                :key="card"
                class="animate"
                :src="`https://zomis-games-cdn.s3.eu-central-1.amazonaws.com/games/dixit/${view.config.cardSet}/${card}`"
                @click="chosenCard(card)"
              />
            </CardZone>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-card>
          <v-card-title>Last round's correct answer</v-card-title>
          <v-card-text>
            <v-img
              class="animate"
              :src="`https://zomis-games-cdn.s3.eu-central-1.amazonaws.com/games/dixit/${view.config.cardSet}/${view.lastAnswer}`"
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CardZone from "@/components/games/common/CardZone"

export default {
  name: "Dixit",
  props: ["view", "actions", "context"],
  components: {
    PlayerProfile, CardZone
  },
  data() {
    return {
      story: ""
    }
  },
  methods: {
    chosenCard(card) {
      if (this.view.action === "story") {
        if (this.story.length > 0) {
          this.actions.actionParameter('story', card + ":" + this.story)
          this.story = "";
        }
      } else if (this.view.action === "place") {
        this.actions.actionParameter('place', card + ":null")
      }
    },
    vote(card) {
      this.actions.actionParameter('vote', card + ":null")
    },
  },
  computed: {
  }
}
</script>
