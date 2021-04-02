context('Game List', () => {
    beforeEach(() => {
        cy.login();
    })

    it('should have a list of games', () => {
        cy.get('.game-type').should('be.gt', 10);
    })
})
