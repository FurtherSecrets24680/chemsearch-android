package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.data.BondOrder
import com.furthersecrets.chemsearch.data.RingTemplate
import com.furthersecrets.chemsearch.data.StructureSearchResultItem
import com.furthersecrets.chemsearch.data.StructureSearchUiState
import com.furthersecrets.chemsearch.data.StructureSketch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureSearchUiBehaviorTest {
    @Test
    fun structureAtomChargeAllowsWiderFormalCharges() {
        assertEquals(8, StructureChargeLimit)
        assertEquals(8, clampStructureAtomCharge(99))
        assertEquals(-8, clampStructureAtomCharge(-99))
        assertEquals(5, clampStructureAtomCharge(5))
    }

    @Test
    fun blockedStructureSearchMessageOnlyPromptsForSingleAtom() {
        val empty = StructureSketch.empty()
        val singleAtom = StructureSketch.empty().addAtom("C", 0.0, 0.0)
        val bonded = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)

        assertEquals(null, structureSearchBlockedMessage(empty))
        assertEquals("Draw at least two connected atoms before searching.", structureSearchBlockedMessage(singleAtom))
        assertEquals(null, structureSearchBlockedMessage(bonded))
    }

    @Test
    fun structureSearchResultsDialogShowsOnlyForLoadingOrMatches() {
        assertFalse(shouldShowStructureSearchResultsDialog(StructureSearchUiState()))
        assertTrue(shouldShowStructureSearchResultsDialog(StructureSearchUiState(isLoading = true)))
        assertTrue(shouldShowStructureSearchResultsDialog(StructureSearchUiState(error = "Structure search failed. Try a simpler drawing.")))
        assertTrue(
            shouldShowStructureSearchResultsDialog(
                StructureSearchUiState(results = listOf(StructureSearchResultItem(cid = 702, title = "Ethanol")))
            )
        )
    }

    @Test
    fun structureSearchResultsDialogSizesAroundVisibleCards() {
        val oneResult = StructureSearchUiState(
            results = listOf(StructureSearchResultItem(cid = 12391, title = "Pentadecane"))
        )
        val threeResults = StructureSearchUiState(
            results = (1L..3L).map { StructureSearchResultItem(cid = it, title = "Match $it") }
        )
        val manyResults = StructureSearchUiState(
            results = (1L..8L).map { StructureSearchResultItem(cid = it, title = "Match $it") }
        )

        assertEquals(1, structureSearchVisibleResultSlots(StructureSearchUiState(isLoading = true)))
        assertEquals(1, structureSearchVisibleResultSlots(oneResult))
        assertEquals(3, structureSearchVisibleResultSlots(threeResults))
        assertEquals(4, structureSearchVisibleResultSlots(manyResults))
    }

    @Test
    fun structureSearchResultsDialogOnlyOpensWhenCurrentScreenSearchArmedIt() {
        val oldResults = StructureSearchUiState(
            results = listOf(StructureSearchResultItem(cid = 12391, title = "Pentadecane"))
        )

        assertFalse(shouldOpenStructureSearchResultsDialog(oldResults, isArmed = false))
        assertTrue(shouldOpenStructureSearchResultsDialog(StructureSearchUiState(isLoading = true), isArmed = true))
        assertTrue(shouldOpenStructureSearchResultsDialog(StructureSearchUiState(error = "Structure search failed."), isArmed = true))
        assertTrue(shouldOpenStructureSearchResultsDialog(oldResults, isArmed = true))
    }

    @Test
    fun selectedTemplateIsPlacedAtTappedCanvasPoint() {
        val sketch = StructureSketch.empty()
        val placed = applyStructureCanvasBlankTap(
            sketch = sketch,
            x = 3.0,
            y = -2.0,
            selectedElement = "C",
            selectedTemplate = RingTemplate.BENZENE,
            selectionMode = false
        )

        assertEquals(6, placed.atoms.size)
        assertEquals(6, placed.bonds.size)
        assertTrue(placed.atoms.any { it.x > 3.0 && it.y < -1.0 })
        assertTrue(placed.atoms.any { it.x < 3.0 && it.y < -1.0 })
    }

    @Test
    fun blankCanvasTapStillPlacesAtomWhenNoTemplateIsSelected() {
        val placed = applyStructureCanvasBlankTap(
            sketch = StructureSketch.empty(),
            x = 3.0,
            y = -2.0,
            selectedElement = "O",
            selectedTemplate = null,
            selectionMode = false
        )

        assertEquals(1, placed.atoms.size)
        assertEquals("O", placed.atoms.first().element)
    }

    @Test
    fun selectModeBlankCanvasTapDoesNotPlaceAtomOrTemplate() {
        val sketch = StructureSketch.empty()

        val blankTap = applyStructureCanvasBlankTap(
            sketch = sketch,
            x = 3.0,
            y = -2.0,
            selectedElement = "O",
            selectedTemplate = null,
            selectionMode = true
        )
        val templateTap = applyStructureCanvasBlankTap(
            sketch = sketch,
            x = 3.0,
            y = -2.0,
            selectedElement = "C",
            selectedTemplate = RingTemplate.BENZENE,
            selectionMode = true
        )

        assertEquals(0, blankTap.atoms.size)
        assertEquals(0, blankTap.bonds.size)
        assertEquals(0, templateTap.atoms.size)
        assertEquals(0, templateTap.bonds.size)
    }

    @Test
    fun selectModeDragMovesAtomsAndBondsWithoutChangingStructure() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)

        val atomMoved = applyStructureCanvasSelectionDrag(
            sketch = sketch,
            draggingAtomId = 1,
            draggingBondId = null,
            atomX = 2.0,
            atomY = 3.0,
            bondDx = 0.0,
            bondDy = 0.0
        )
        val bondMoved = applyStructureCanvasSelectionDrag(
            sketch = sketch,
            draggingAtomId = null,
            draggingBondId = 1,
            atomX = 0.0,
            atomY = 0.0,
            bondDx = 0.5,
            bondDy = -0.25
        )

        assertEquals(2.0, atomMoved.atoms.first { it.id == 1 }.x, 0.0001)
        assertEquals(3.0, atomMoved.atoms.first { it.id == 1 }.y, 0.0001)
        assertEquals(2, atomMoved.atoms.size)
        assertEquals(1, atomMoved.bonds.size)

        assertEquals(0.5, bondMoved.atoms.first { it.id == 1 }.x, 0.0001)
        assertEquals(-0.25, bondMoved.atoms.first { it.id == 1 }.y, 0.0001)
        assertEquals(1.9, bondMoved.atoms.first { it.id == 2 }.x, 0.0001)
        assertEquals(-0.25, bondMoved.atoms.first { it.id == 2 }.y, 0.0001)
        assertEquals(BondOrder.SINGLE.molfileValue, bondMoved.bonds.first().order)
    }

    @Test
    fun selectModeDragMovesWholeMoleculeWhenMoveButtonSelectedIt() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)

        val moved = applyStructureCanvasSelectionDrag(
            sketch = sketch,
            draggingMolecule = true,
            draggingAtomId = null,
            draggingBondId = null,
            atomX = 0.0,
            atomY = 0.0,
            bondDx = 0.5,
            bondDy = -0.25
        )

        assertEquals(0.5, moved.atoms.first { it.id == 1 }.x, 0.0001)
        assertEquals(-0.25, moved.atoms.first { it.id == 1 }.y, 0.0001)
        assertEquals(1.9, moved.atoms.first { it.id == 2 }.x, 0.0001)
        assertEquals(-0.25, moved.atoms.first { it.id == 2 }.y, 0.0001)
        assertEquals(1, moved.bonds.size)
    }
}
