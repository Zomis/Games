const path = "https://d3ux78k3bc7mem.cloudfront.net/games/alc/";
const artifactIds = {
    "Periscope": 0,
    "Magic Mortar": 1,
    "Boots of Speed": 2,
    "Discount Card": 3,
    "Printing Press": 4,
    "Robe of Respect": 5,
    "Silver Chalice": 6,
    "Amulet of Rhethoric": 7,
    "Thinking Cap": 8,
    "Witch's Trunk": 9,
    "Hypnotic Amulet": 10,
    "Seal of Authority": 11,
    "Feather in Cap": 12,
    "Crystal Cabinet": 13,
    "Altar of Gold": 14,
    "Wisdom Idol": 15,
    "Magic Mirror": 16,
    "Bronze Cup": 17,
    "Ring of Favor": 18,
}

const Favor = {
    props: ['name', 'onClick'],
    methods: {
        click() {
            this.onClick(this.name);
        }
    },
    computed: {
        favorId() {
            switch (this.name) {
                case "ASSISTANT": return 0;
                case "ASSOCIATE": return 1;
                case "BARMAID": return 2;
                case "CUSTODIAN": return 3;
                case "HERBALIST": return 4;
                case "MERCHANT": return 5;
                case "SHOPKEEPER": return 6;
                case "SAGE": return 7;
                default: return -1;
            }
        },
        imageSource() {
            return `${path}favour_${this.favorId}.jpg`;
        },
    },
    render() {
        return (<img src={this.imageSource} onClick={this.click} class="gamecard" />);
    } 
};
const Ingredient = {
    props: ['id', 'onClick'],
    methods: {
        click() {
            this.onClick(this.id);
        }
    },
    computed: {
        imageSource() {
            return `${path}ingredient_${this.id}.png`;
        }
    },
    render() {
      return (<img src={this.imageSource} onClick={this.click} class="gamecard" />);
    }
};
const Artifact = {
    props: ['name', 'onClick'],
    methods: {
        click() {
            this.onClick(this.name);
        }
    },
    computed: {
        artifactId() {
            return artifactIds[this.name];
        },
        imageSource() {
            return `${path}artifact_${this.artifactId}.jpg`;
        }
    },
    render() {
      return (<img class="gamecard" onClick={this.click} src={this.imageSource} />);
    } 
};
const Cube = {
    props: ['playerIndex'],
    computed: {
        imageSource() {
            return `${path}cube_${this.playerIndex}.png`;
        }
    },
    render() {
      return (<img class="cube" src={this.imageSource} />);
    } 
};


export default {
    Cube: Cube,
    Favor: Favor,
    Ingredient: Ingredient,
    Artifact: Artifact,
}
