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

const iconsMap = {
    ATTACK: 'mdi-sword',
    PLAY_AGAIN: 'mdi-flash',
    HEAL: 'mdi-heart',
    DRAW: 'mdi-plus-box-multiple',
    SHIELD: 'mdi-shield',
    FIREBALL: 'mdi-fire',
    STEAL_SHIELD: 'mdi-shield-home',
    SWAP_HITPOINTS: 'mdi-rotate-3d-variant',
    PICK_UP_CARD: 'mdi-delete-restore',
    DESTROY_ALL_SHIELDS: 'mdi-shield-off',
    PROTECTION_ONE_TURN: 'mdi-account-lock',
    DESTROY_SINGLE_SHIELD: 'mdi-shield-half-full',
    STEAL_CARD: 'mdi-credit-card-scan',
    HEAL_AND_ATTACK_FOR_EACH_OPPONENT: 'mdi-hexagram-outline',
    ALL_DISCARD_AND_DRAW: 'mdi-account-box-multiple'
}

export default {
    name: "DungeonMayhemCard",
    props: ["card", "actionable", "actions"],
    data() {
        return {
            icons: iconsMap
        }
    },
    components: {
        Actionable
    }
}
</script>
