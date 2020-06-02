<template>
    <Actionable :actions="actions" :actionable="actionable">
        <v-card>
            <v-card-title>
                <div :class="{ actionable: actions.available[action] }">
                    {{ card.name }}
                </div>
            </v-card-title>
            <v-card-text>
                <div v-if="card.health">
                    {{ card.health }}
                </div>
                <div v-else>
                    <v-tooltip bottom v-for="(symbol, index) in card.symbols" :key="index" >
                        <template v-slot:activator="{ on }">
                            <v-icon v-on="on">{{ icons[symbol] }}</v-icon>
                        </template>
                        <span>{{ symbol }}</span>
                    </v-tooltip>
                </div>
            </v-card-text>
        </v-card>
    </Actionable>
</template>
<script>
import Actionable from "@/components/games/common/Actionable"

export default {
    name: "DungeonMayhemCard",
    props: ["card", "actionable", "actions", "icons"],
    components: {
        Actionable
    }
}
</script>
