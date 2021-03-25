const PercyScript = require('@percy/script');

const BASE_URL = 'http://localhost:8080';
const AUTH_COOKIE = 'cookie:cookie:C3qH3VVmoqR/rbxJIiVys1jW5jE0bDtuixWVorDfJhE='

PercyScript.run(async (page, percySnapshot) => {
    try {
        await page.evaluateOnNewDocument(
            token => {
                localStorage.clear();
                localStorage.setItem('authCookie', token);
                localStorage.setItem('lastUsedProvider', 'guest');
                localStorage.setItem('cookie:accepted', 'true');
            }, AUTH_COOKIE);

        await page.goto(BASE_URL, { waitUntil: 'load' });

        const snap = camera(page, percySnapshot);

        await page.waitForSelector('.game-type');
        await percySnapshot('homepage');

        await snap('spice-road');
        await goBackToGameList(page);
        await snap('dixit');

        await page.close();
        process.exit(0);
    } catch (err) {
        console.error(err);
    }
});

const camera = (page, percySnapshot) => async (game) => {
    await page.click(`.game-${game} .v-btn`);
    await page.waitForSelector('.game');
    await page.waitForSelector('.animation-list-complete');
    // TODO: add extra waitFor to make sure the game has loaded. Perhaps check on some status instead.
    await percySnapshot(game);
}

const goBackToGameList = async (page) => {
    await page.click('.router-link-active');
    await page.waitForSelector('.game-type');
}