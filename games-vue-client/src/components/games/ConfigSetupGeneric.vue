<template>
    <v-card class="config-setup">
        <v-row>
            <v-col v-for="property in configProperties" :key="property.name">
                <v-switch v-if="property.type === 'boolean'" v-model="config[property.name]" :label="property.name" />
                <v-text-field v-else-if="property.type === 'number'" v-model="config[property.name]"
                type="number"
                :label="property.name" :placeholder="property.name"
                />
                <span v-else>
                    Unknown property type {{ property.type }} for {{ property.name }}
                </span>
            </v-col>
        </v-row>
    </v-card>
</template>
<script>
export default {
    name: "ConfigSetupGeneric",
    props: ["config"],
    data() {
        return {
            configProperties: Object.keys(this.config).map(key => ({
                name: key,
                type: typeof this.config[key],
                value: this.config[key]
            }))
        }
    }
}
</script>
