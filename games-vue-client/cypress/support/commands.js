import '@percy/cypress';
import { BASE_URL, GUEST_TOKEN } from '../utils/constants';

// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })

Cypress.Commands.add('login', () => {
    cy.visit(BASE_URL)
        .then(() => {
            window.localStorage.setItem('lastUsedProvider', 'guest');
            window.localStorage.setItem('authCookie', GUEST_TOKEN);
            window.localStorage.setItem('cookie:accepted', 'true');
        });
    cy.get('[class="server-selection"]').should('be.visible');
    cy.reload();
})