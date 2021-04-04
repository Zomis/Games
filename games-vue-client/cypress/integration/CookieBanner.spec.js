import { BASE_URL } from "../utils/constants";

context('Cookie Banner', () => {
    beforeEach(() => {
        cy.visit(BASE_URL);
    })

    it('should provide a cookie banner', () => {
        cy.get('[class="Cookie__button"]').should('be.visible');
    })

    it('should not display a cookie banner after accept and reload', () => {
        cy.get('[class="Cookie__button"]').should('be.visible');
        cy.get('[class="Cookie__button"]').click();
        cy.get('[class="Cookie__button"]').should('not.exist');

        cy.reload();

        cy.get('[class="Cookie__button"]').should('not.exist');
    })
})
