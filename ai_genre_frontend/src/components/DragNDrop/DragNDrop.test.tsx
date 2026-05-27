import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from "vitest";
import DragNDrop from "./DragNDrop";
import '@testing-library/jest-dom';
import { useState } from 'react';

// ---------------- Helper ----------------

/**
 * Creates a new file using the given name, type, and size
 * @param name The name for the new file.
 * @param type The file type for the new file.
 * @param size The size of the new file (in bytes).
 * @returns File type object of the new file.
 */
const makeFile = (name: string, type = 'audio/wav', size = 1024): File => {
    const buffer = new Uint8Array(size);
    return new File([buffer], name, { type });
};

/**
 * Creates a DragEvent with the list of files.
 * @param files The files to be uploaded.
 * @returns DragEvent representing the file drag event.
 */
const makeDragEvent = (files: File[]): Partial<React.DragEvent<HTMLDivElement>> => ({
    preventDefault: vi.fn(),
    dataTransfer: {
        files: files,
        types: ['Files'],
    } as unknown as DataTransfer
});

// ---------------- Setup ----------------

/**
 * Creates wrapper component to allow for useState on file and setFile
 */
const WrapperComponent = ({ setError = vi.fn() }) => {
    const [file, setFile] = useState<File | null>(null);

    return (
        <DragNDrop
            file={file}
            setFile={setFile}
            setError={setError}
        />
    );
};

/**
 * Sets up the DragNDrop file component with mocked inputs
 * @returns The mocked inputs
 */
const setup = () => {
    // Mock the inputs (file, setFile, setError) and render the component
    const setError = vi.fn();

    const utils = render(
        <WrapperComponent setError={setError} />
    );

    // Gets the dropzone element for the drag and drop file input
    const dropzone = () => screen.getByText('Drop your audio file here').closest('div')!.parentElement!;

    return {setError, dropzone, ...utils};
};

// ---------------- Tests ----------------
describe('DragNDrop', () => {

    // ---------------- Initial Render ----------------

    describe('initial render', () => {
        it('renders the upload prompt', () => {
            const {dropzone} = setup();
            // Check the dropzone is there, the file pill is not
            expect(dropzone()).toBeInTheDocument();
            expect(screen.queryByRole('button', {name: /remove file/i})).not.toBeInTheDocument();
        });
    });

    // ---------------- File Input With Browser ----------------
    // Since the current implementation has an 'accept' value on the input field, testing invalid file types can only be done with drag or drop or removing the param. 
    describe('file input with browser', () => {
        it('shows the file pill after a valid file is selected', async () => {
            setup();
            const input = document.getElementById('browse');
            const file = makeFile('audio.wav');

            await userEvent.upload(input!, file);

            // Expect the file pill to be shown
            expect(screen.getByText('audio.wav')).toBeInTheDocument();
            expect(screen.queryByRole('button', {name: /remove file/i})).toBeInTheDocument();
        });
    });

    describe('drag and drop file', () => {
        it('sets the file when a valid file is dragged and dropped', async () => {
            const {dropzone} = setup();
            const file = makeFile('audio.wav');
            const event = makeDragEvent([file]);

            // If the userEvent fails, manually fire the event
            fireEvent.drop(dropzone(), event);

            expect(screen.getByText('audio.wav')).toBeInTheDocument();
        });

        it('handles invalid files correctly', async () => {
            const {setError, dropzone} = setup();
            const file = makeFile('audio.txt', 'text/plain');
            const event = makeDragEvent([file]);

            fireEvent.drop(dropzone(), event);

            // Error should be set and there should be no file pill (otherwise the file was set to the invalid file)
            expect(setError).toHaveBeenCalledTimes(1);
            expect(setError).not.toHaveBeenCalledWith(null);
            expect(screen.queryByRole('button', {name: /remove file/i})).not.toBeInTheDocument();
        });

        it('clears error on valid file', () => {
            const {setError, dropzone} = setup();
            const invalidFile = makeFile('audio.txt', 'text/plain');
            const invalidEvent = makeDragEvent([invalidFile]);
            const validFile = makeFile('audio.wav');
            const validEvent = makeDragEvent([validFile]);

            // Run invalid file for error, then valid for no error
            fireEvent.drop(dropzone(), invalidEvent);
            // Do not need to check the error was set, since previous test accounts for this
            fireEvent.drop(dropzone(), validEvent);

            // setError should be reset to null and the file pill should now be in the document
            expect(setError).toHaveBeenCalledTimes(2);
            expect(screen.queryByRole('button', {name: /remove file/i})).toBeInTheDocument();
        });

        it('calls and sets error on no file', () => {
            const {setError, dropzone} = setup();
            const event = makeDragEvent([]);

            fireEvent.drop(dropzone(), event);

            // 'No file' error should be made and there should be an error value
            expect(setError).toHaveBeenCalled();
            expect(setError).not.toHaveBeenCalledWith(null);
        });
    });

    describe('removing a file', async ()=> {
        it('resets the state after a file is removed', async () => {
            setup();
            const input = document.getElementById('browse')!;
            const file = makeFile('audio.wav');

            // Upload a valid file, then remove the file should revert all values and the pill should be hidden
            await userEvent.upload(input, file);
            const removeBtn = screen.queryByRole('button', {name: /remove file/i})!;
            await userEvent.click(removeBtn);

            // Button should not be found and browse input show have no value
            expect(screen.queryByRole('button', {name: /remove file/i})).not.toBeInTheDocument();
            expect((document.getElementById('browse') as HTMLInputElement).value).toBe('');
        });

        it('it allows the same file to be re-uploaded', async () => {
            setup();
            const input = document.getElementById('browse')!;
            const file = makeFile('audio.wav');

            // Upload file, remove it, re-upload should have the file
            await userEvent.upload(input, file);
            
            // remove the file
            const removeBtn = screen.queryByRole('buttton', {name: /remove file/i})!;
            await userEvent.click(removeBtn);

            // Re-upload button
            const secondInput = document.getElementById('browse')!;
            await userEvent.upload(secondInput, file);

            expect(screen.queryByRole('button', {name: /remove file/i})).toBeInTheDocument();
        });
    });
});