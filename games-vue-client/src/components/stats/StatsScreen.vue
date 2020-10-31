<template>
    <v-container fluid>
        <v-row dense>
            <v-col cols="4">
                <v-text-field v-model="playerConditions" label="Player Ids" />
            </v-col>
            <v-col cols="2">
                <v-select :items="supportedGames" item-text="text" item-value="value" v-model="tagConditions">
                </v-select>
            </v-col>
            <v-col cols="2">
                <v-btn @click="query()">Apply filter</v-btn>
            </v-col>
        </v-row>
        <v-row dense v-for="game in gameList" :key="game.gameId">
            <v-col cols="12">
                <v-card>
                    <v-card-title class="subheading font-weight-bold">
                        <v-icon color="blue">mdi-replay</v-icon>
                        <router-link :to="`/stats/games/${game.gameId}/replay`">
                            {{ game.title }}
                        </router-link>
                    </v-card-title>

                    <v-divider />
                    <v-list>
                        <v-list-item
                            v-for="(player, index) in game.players"
                            :key="index"
                        >
                            <v-list-item-content>{{ player.playerId }}</v-list-item-content>
                            <v-list-item-content class="align-end">{{ player.resultPosition }}</v-list-item-content>
                        </v-list-item>
                    </v-list>
                    <v-divider />
                    <v-list>
                        <v-list-item
                            v-for="(tag, index) in game.tags"
                            :key="index"
                        >
                            <v-list-item-content>{{ tag.tagId }}</v-list-item-content>
                            <v-list-item-content class="align-end">{{ tag.tagParameter }}</v-list-item-content>
                        </v-list-item>
                    </v-list>
                </v-card>
            </v-col>
        </v-row>

        <!--
            Show games from <timeperiod> (24 hours)
                - How to query on a time period?
            
            Search player name
                - How to search and list a player's name?

            Query based on tags and players
                - Tags - success!

            For each game show: Players playing, tags.
        -->
    </v-container>
</template>
<script>
import axios from "axios"
import supportedGames from "@/supportedGames"

const buildTitleFromGame = (game) => {
    const titleTag = game.tags.reduce((acc, { tagId }) => {
        const pre = acc.length > 0 ? ' ' : '';
        const tagName = tagId.substring(5, tagId.length);

        return `${pre}${acc} ${tagName}`;
    }, '');
    const shortGameId = game.gameId.substring(0, 8);

    return `${titleTag} - ${shortGameId}`;
}

const appendedData = (data) => data.map((game) => {
    return { ...game, title: buildTitleFromGame(game) };
});

const mockedData = [{"gameId":"007748a7-c4a8-443f-9b90-ff1819d42174","players":[{"playerId":"b4c6bb4b-3430-4384-9670-3b6e80f7ee17","result":10,"resultPosition":1},{"playerId":"29dac597-f2b2-4378-80dc-b31c2d3edb2d","result":1,"resultPosition":4},{"playerId":"29dac597-f2b2-4378-80dc-b31c2d3edb2d","result":12,"resultPosition":3},{"playerId":"c4ec6785-c78f-4bba-9133-5f3dfe35c369","result":12,"resultPosition":2}],"tags":[{"tagId":"type/Artax","tagParameter":1585233148}]},{"gameId":"098619fe-9b97-4d47-88df-de9d005511ad","players":[{"playerId":"3a4d4cfa-9dc3-4701-a971-d21036abf127","result":14,"resultPosition":2},{"playerId":"b4c6bb4b-3430-4384-9670-3b6e80f7ee17","result":15,"resultPosition":2}],"tags":[{"tagId":"type/Hanabi","tagParameter":1588378068}]},{"gameId":"0b14b4f5-651a-4366-a3de-c902242470e3","players":[{"playerId":"b4c6bb4b-3430-4384-9670-3b6e80f7ee17","result":12,"resultPosition":5},{"playerId":"c63307d1-d284-4f98-96f7-e15cd8e95e48","result":12,"resultPosition":5},{"playerId":"6fd16ddd-8386-4c24-883c-0ae833effbdd","result":11,"resultPosition":5},{"playerId":"39c79b22-0247-4b49-a58d-e747bf4c0d9a","result":3,"resultPosition":5},{"playerId":"1cc08b1c-e904-4128-ba34-40fd9f17b087","result":2,"resultPosition":5}],"tags":[{"tagId":"type/Hanabi","tagParameter":1588793132},{"tagId":"type/Artax","tagParameter":1585233148}]}];

export default {
    name: "StatsScreen",
    props: ["players", "tags"],
    data() {
        return {
            supportedGames: supportedGames.enabledGamesTextValue(),
            gameList: appendedData(mockedData), // run query onload?
            playerConditions: this.players ? this.players : "", // TODO update url when filter is applied?
            tagConditions: this.tags ? this.tags : "",
            chosenTag: "",
        }
    },
    methods: {
        query() {
            let cleanURI = encodeURI(`${this.baseURL}stats/query?players=${this.playerConditions}&tags=${this.tagConditions}`).replace(/#/g, '%23')
            axios.get(cleanURI).then(response => {
                console.log(response.data);
                this.gameList = appendedData(response.data);
            })
        }
    },
    computed: {
        baseURL() {
            return 'https://games.zomis.net:42638/' // process.env.VUE_APP_URL
        }
    }
}
</script>