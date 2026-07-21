import { render, screen } from '@testing-library/react';
import { Button } from './button';

test('renders with the default variant classes and is clickable', () => {
  render(<Button>Click me</Button>);
  const btn = screen.getByRole('button', { name: /click me/i });
  expect(btn).toBeInTheDocument();
  expect(btn.className).toMatch(/bg-primary/);
});

test('applies the destructive variant', () => {
  render(<Button variant="destructive">Delete</Button>);
  const btn = screen.getByRole('button', { name: /delete/i });
  expect(btn.className).toMatch(/bg-destructive/);
});
