<template>
    <v-container fluid>
        <v-treeview
            activatable hoverable open-on-click
            :items="viewItems"
            :open.sync="viewOpen">
            <template v-slot:prepend="{ item }">
                <v-icon v-if="item.children" v-text="`mdi-${item.id === 'root' ? 'home-variant' : 'folder-network'}`" />
            </template>
        </v-treeview>
        
        <v-treeview
            activatable hoverable open-on-click return-object
            :active.sync="actionActive"
            :items="actionItems"
            :open.sync="actionOpen">
            <template v-slot:prepend="{ item }">
                <v-icon v-if="item.children" v-text="`mdi-${item.id === 1 ? 'home-variant' : 'folder-network'}`" />
            </template>
        </v-treeview>
        <v-btn @click="performAction">Perform Action</v-btn>
    </v-container>
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
    name: "TreeViewGame",
    props: ["view", "actions", "actionChoice", "onAction", "players"],
    data() {
        return {
            viewOpen: [],
            actionActive: [],
            actionOpen: []
        }
    },
    methods: {
        performAction() {
            console.log(this.actionActive)
            if (this.actionActive.length !== 1) {
                window.alert("You must select one and only one item to perform an action.");
                return;
            }
            let key = this.actionActive[0].id;
            let keyParts = key.split("/");
            if (keyParts.length !== 5) {
                window.alert("You must do this when having selected a 'direct' item");
                return;
            }
            let actionName = keyParts[2];
            let sub = keyParts[3];
            console.log("TreeView selected", actionName, sub);
            this.onAction(actionName, sub);

            // /root/takeMoney/take-GREEN/direct
        }
    },
    computed: {
        viewItems() {
            return [convertToItems(convertToItems, "", "View", this.view)];
        },
        actionItems() {
            return [convertToItems(convertToItems, "", "Actions", this.actions)];
        }
    }
}
</script>