<template>
    <div>
        <v-treeview
            activatable hoverable open-on-click :return-object="isActions"
            :active.sync="active"
            :items="items"
            :open.sync="open">
            <template v-slot:prepend="{ item }">
                <v-icon v-if="item.children" v-text="`mdi-${item.id === 'root' ? 'home-variant' : 'folder-network'}`" />
            </template>
        </v-treeview>
        <v-btn v-if="isActions" @click="performAction">Perform Action</v-btn>
    </div>
</template>
<script>
function convertToItems(recursiveFunction, parentId, key, data) {
    let nextParentId = parentId + '/' + key;
    let basic = {
        id: nextParentId,
        name: key
    };
    if (typeof data === 'object' && data !== null) {
        let keys = Object.keys(data);
        return { ...basic, children: keys.map(k => recursiveFunction(recursiveFunction, nextParentId, k, data[k])) }
    }
    if (Array.isArray(data)) {
        return { ...basic, children: data.map((value, index) => recursiveFunction(recursiveFunction, nextParentId, index, value)) }
    }
    return {
        id: parentId + '/' + key,
        name: key + ' = ' + data
    }
}

export default {
    name: "GameTreeView",
    props: ["view", "actions"],
    data() {
        return {
            open: [],
            active: []
        }
    },
    methods: {
        performAction() {
            console.log(this.active)
            if (this.active.length !== 1) {
                window.alert("You must select one and only one item to perform an action.");
                return;
            }
            let key = this.active[0].id;
            let keyParts = key.split("/");
            if (keyParts.length !== 5) {
                window.alert("You must do this when having selected a 'direct' item");
                return;
            }
            let actionName = keyParts[2];
            let sub = keyParts[3];
            console.log("TreeView selected", actionName, sub);
            this.actions.perform('ignored', sub);

            // /root/takeMoney/take-GREEN/direct
        }
    },
    computed: {
        isActions() {
            return typeof this.actions !== 'undefined'
        },
        items() {
            if (this.isActions) {
                return [convertToItems(convertToItems, "", "Actions", this.actions.available)];
            } else {
                return [convertToItems(convertToItems, "", "View", this.view)];
            }
        }
    }
}
</script>
