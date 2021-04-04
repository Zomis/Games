const PercyScript = require('@percy/script');

const BASE_URL = 'http://localhost:8080';
const AUTH_COOKIE = 'C3qH3VVmoqR/rbxJIiVys1jW5jE0bDtuixWVorDfJhE='

PercyScript.run(async (page, percySnapshot) => {
    try {
        await login(page);
        await page.waitForSelector('.game-type');

        const snapshotGame = gameCamera(page, percySnapshot);
        await percySnapshot('homepage');
        await snapshotGame('spice-road', '.list-complete-item', 15);
        await snapshotGame('dixit', '.v-image__image', 7);
        await snapshotGame('coup', '.actions-table', 1);

        await page.close();
        process.exit(0);
    } catch (err) {
        console.error(err);
    }
});

const gameCamera = (page, percySnapshot) => async (game, selector, expectedItems) => {
    await page.click(`.game-${game} .v-btn`);
    await page.waitForSelector('.game');
    await page.waitForSelector('.animation-list-complete');

    if (selector !== undefined) {
        await page.waitForFunction(`document.querySelectorAll("${selector}") && document.querySelectorAll("${selector}").length === ${expectedItems}`);
    }

    await percySnapshot(game);
    await goBackToGameList(page);
}

const goBackToGameList = async (page) => {
    await page.click('.router-link-active');
    await page.waitForSelector('.game-type');
}

const login = async (page) => {
    await page.evaluateOnNewDocument(
        token => {
            localStorage.clear();
            localStorage.setItem('authCookie', token);
            localStorage.setItem('lastUsedProvider', 'guest');
            localStorage.setItem('cookie:accepted', 'true');
        }, AUTH_COOKIE);

    await page.goto(BASE_URL, { waitUntil: 'load' });
}