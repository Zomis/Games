<template>
  <v-menu
    v-if="shouldBeVisible"
    v-model="showMenu"
    bottom
    offset-y
    :disabled="!useMenu"
    :close-on-content-click="!this.stickyMenu"
  >
    <template v-slot:activator="{ on: menu }">
      <v-tooltip
        bottom
        disabled
      >
        <template v-slot:activator="{ on: tooltip }">
          <component
            :is="component"
            :class="cssClass"
            :disabled="!isActionable"
            @click="componentClick()"
            v-on="{ ...tooltip, ...menu }"
          >
            <slot />
            <!-- Actionable button v-for=... :key :actionable="card" actionType="play" :icon="icons[card]" /> -->
            <!-- Actionable button :actionType="['bet', 'pass']" -->
            <!-- Maybe use an action-button to select which available bet action you want to perform, or if you want to pass -->
            <!-- Outer component can be: v-btn, div, v-icon, v-menu... -->
          </component>
        </template>
        <span>{{ tooltip }}</span>
      </v-tooltip>
    </template>
    <v-row v-if="useMenu">
      <v-col
        v-for="item in menuItems"
        :key="item"
        class="pa-0"
      >
        <ActionButton
          :actions=actions
          :item=item
          :descriptions=desc
        >
        </ActionButton>
      </v-col>
    </v-row>
  </v-menu>
</template>

<script>
import { VBtn } from 'vuetify/lib'
import ActionButton from './ActionButton'

export default {
    name: "Actionable",
    props: {
        actionable: { type: String, required: false },
        actionType: {
            validator(value) { return typeof value === 'string' || Array.isArray(value) }
        },
        hideIfIrrelevant: { type: Boolean, default: false },
        stickyMenu: { type: Boolean, default: false },
        actions: { type: Object, required: true },
        button: { type: Boolean, default: false },
        icon: { type: String, required: false },
        value: { type: Number, required: false }
    },
    data() {
        return {
            showMenu: false,
            desc: {"ACTION-TAX": "desc"}
        }
    },
    components: {
        VBtn,
        ActionButton
    },
    methods: {
        componentClick() {
            if (this.actionable) {
                this.actions.perform('', this.actionable)
            }
        }
    },
    computed: {
        shouldBeVisible() {
            return !this.hideIfIrrelevant || this.isActionable
        },
        useMenu() {
            return !this.actionable // or if there are multiple actions for this actionable (such as both 'Play' and 'Discard' using the same parameter)
        },
        isActionable() {
            return this.menuItems.length > 0 || (this.actionable && this.actions.available[this.actionable])
        },
        cssClass() {
            return {
                actionable: this.isActionable,
                ['actionable-wrapper']: true
            }
        },
        component() {
            if (this.button) return 'v-btn'
            return 'div'
        },
        tooltip() {
            return 'Disabled'
        },
        menuItems() {
            if (!this.actionable && this.actionType) {
                return Object.keys(this.actions.available).filter(act => this.actionType.includes(this.actions.available[act].actionType))
            }
            return []
        }
    }
}
</script>
<style scoped>
.actionable-wrapper {
    display: inline-block
}

.actionable {
    border-style: solid !important;
    border-width: thick !important;
    border-color: #ffd166 !important;
}
</style>