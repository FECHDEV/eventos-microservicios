import { describe, expect, it } from 'vitest';

import { CONTRASENA_SEGURA } from './reglas';

// CU-01 RF-16: la misma regla que RegistroClienteRequestTest
describe('CONTRASENA_SEGURA', () => {
	it('acepta 8 caracteres con mayúscula, minúscula y número', () => {
		expect(CONTRASENA_SEGURA.test('Abcdef12')).toBe(true);
	});

	it('rechaza si falta el largo, la mayúscula, la minúscula o el número', () => {
		for (const contrasena of ['Abcdef1', 'secreta12', 'SECRETA12', 'Secretaaa']) {
			expect(CONTRASENA_SEGURA.test(contrasena)).toBe(false);
		}
	});

	it('un emoji cuenta como un carácter, como en el backend', () => {
		expect(CONTRASENA_SEGURA.test('Aa1b😀😀')).toBe(false);
	});

	it('acepta saltos de línea, como el backend', () => {
		expect(CONTRASENA_SEGURA.test('Secreta12\nabc')).toBe(true);
	});
});
