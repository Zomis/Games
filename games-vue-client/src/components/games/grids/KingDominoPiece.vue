<template>
  <v-card @click="click">
    <v-card-text>
      <div>
        <span>{{ domino.value }}</span>
        <span>
          <PlayerProfile
            v-if="showOwner && domino.owner !== null"
            :context="context"
            :player-index="domino.owner"
            :size="16"
            show-name
          />
        </span>
      </div>
      <KingDominoTile :tile="domino.first" />
      <KingDominoTile :tile="domino.second" />
    </v-card-text>
  </v-card>
</template>
<script>
import KingDominoTile from "./KingDominoTile";
import PlayerProfile from "@/components/games/common/PlayerProfile"

export default {
  name: "KingDominoPiece",
  props: ["domino", "onClick", "showOwner", "context"],
  components: {
    KingDominoTile, PlayerProfile
  },
  methods: {
    click() {
      if (this.onClick) {
        this.onClick(this.domino)
      }
    }
  }
}
</script>