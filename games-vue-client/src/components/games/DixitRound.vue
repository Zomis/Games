<template>
  <v-card>
    <v-card-title>
      <h1>Round {{ results.number }}</h1>
    </v-card-title>
    <v-card-text>
      <v-row>
        <v-col>
          <span style="font-style: italic">{{ results.story }}</span>
        </v-col>
      </v-row>
      <v-row>
        <v-col
          v-for="(player, index) in results.cards"
          :key="index"
        >
          <v-row>
            <v-col>
              <PlayerProfile
                :size="32"
                :context="context"
                :player-index="player.playerIndex"
                show-name
              />
              <v-icon v-if="player.storyteller">
                mdi-chat
              </v-icon>
            </v-col>
          </v-row>
          <v-row>
            <v-col>
              <v-img
                :src="`https://zomis-games-cdn.s3.eu-central-1.amazonaws.com/games/dixit/${results.cardSet}/${player.card}`"
              />
            </v-col>
          </v-row>
          <v-row
            v-for="voter in player.firstVotes"
            :key="voter"
          >
            <PlayerProfile
              :size="32"
              :context="context"
              :player-index="voter"
              show-name
            />
          </v-row>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"

export default {
  name: "DixitRound",
  props: ["results", "context"],
  components: {
    PlayerProfile
  }
}
</script>
