context('Game List', () => {
    beforeEach(() => {
        cy.login();
    })

    it('should have a list of games', () => {
        cy.get('.game-type').should('have.length', 18);
    })
})
