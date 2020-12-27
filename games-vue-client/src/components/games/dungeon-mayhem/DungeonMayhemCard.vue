<template>
  <Actionable
    :actions="actions"
    :actionable="actionable"
  >
    <v-card>
      <v-card-title>
        <div>
          {{ card.name }}
        </div>
      </v-card-title>
      <v-card-text>
        <div v-if="card.health">
          {{ card.health }}
        </div>
        <div v-else>
          <v-tooltip
            v-for="(symbol, index) in card.symbols"
            :key="index"
            bottom
          >
            <template v-slot:activator="{ on }">
              <v-icon
                :color="symbolMap[symbol].color"
                v-on="on"
              >
                {{ symbolMap[symbol].icon }}
              </v-icon>
            </template>
            <span>{{ symbolMap[symbol].text }}</span>
          </v-tooltip>
        </div>
      </v-card-text>
    </v-card>
  </Actionable>
</template>
<script>
import Actionable from "@/components/games/common/Actionable"
import dungeonMayhemSymbols from "./dungeonMayhemSymbols"

export default {
    name: "DungeonMayhemCard",
    props: ["card", "actionable", "actions"],
    computed: {
        symbolMap: () => dungeonMayhemSymbols
    },
    components: {
        Actionable
    }
}
</script>
