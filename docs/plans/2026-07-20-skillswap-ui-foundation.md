# SkillSwap Hub — UI Plan 1: Foundation (Tailwind + shadcn/ui, App Shell, Auth, Dashboard)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The frontend gets Tailwind CSS + shadcn/ui wired in, a proper authenticated app shell (header, nav, avatar/logout menu), a restyled Login/Register, and a real Dashboard pulling live data from the backend — replacing the bare unstyled scaffold from Plan 1 with a presentable, professional starting point every later UI plan builds on.

**Architecture:** shadcn/ui is not an npm component library — its components are plain React source files you own, styled with Tailwind and built on Radix UI primitives + `class-variance-authority` for variants. This plan adds Tailwind v4 (via the official `@tailwindcss/vite` plugin — no `tailwind.config.js`/PostCSS config needed, themed entirely through CSS custom properties in one stylesheet) and hand-writes the four foundational shadcn components this plan's pages need (`Button`, `Card`, `Input`, `Label`) plus the shell-specific ones (`Avatar`, `Badge`, `DropdownMenu`, `Separator`) exactly as the real shadcn/ui "new-york" style would generate them — deterministic and reviewable, with no dependency on the `shadcn` CLI's network calls inside an automated task. `AuthContext` gains one small addition (fetch `/api/me` on mount when a token exists but `user` is still null) so the nav and every future page can reliably read the current user's name/role after a hard refresh, not just right after login.

**Tech Stack:** React 18, Vite 5, Tailwind CSS v4, shadcn/ui (new-york style, Radix UI primitives), `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react` (icons). Vitest + React Testing Library for tests (already configured).

## Global Constraints

- All new source files live under `frontend/src/`. Path alias `@/*` → `frontend/src/*` (matches shadcn/ui convention) — configured in `vite.config.js`.
- **No `tailwind.config.js`.** Tailwind v4's Vite plugin (`@tailwindcss/vite`) needs zero config file; all theme tokens live in `frontend/src/index.css` via `@theme inline` and CSS custom properties. Do not add a config file "just in case."
- shadcn component files go in `frontend/src/components/ui/` (lowercase filenames, e.g. `button.jsx`) — the established shadcn convention, so any future `npx shadcn add` a human runs later lands in the same place without conflict.
- Every shadcn primitive written in this plan must match the actual upstream shadcn/ui "new-york" style output exactly (variants, class names, prop shapes) — this is copy-paste boilerplate, not a place to improvise a "simpler" version; a later `npx shadcn add <component>` should be able to add siblings without clashing with this plan's conventions.
- The existing `Login.test.jsx` assertions (`heading /log in/i`, `getByLabelText(/email/i)`, `getByLabelText(/password/i)`, `button /log in/i`) must keep passing without modification — the restyle changes markup/classes, not the accessible name/role/label structure the test depends on.
- Existing files this plan touches (`App.jsx`, `AuthContext.jsx`, `Login.jsx`, `Register.jsx`, `Dashboard.jsx`, `main.jsx`, `vite.config.js`, `package.json`) follow Plan 1's established patterns (Axios client at `src/api/client.js`, `useAuth()` hook, `ProtectedRoute`) — extend them, don't restructure what isn't in scope.
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add `Co-Authored-By` or AI attribution. Conventional Commit messages. Commit at the end of every task.
- Verification for UI tasks is `npm run build` (must succeed) plus `npm test` (must stay green) — strict RED-before-GREEN TDD doesn't meaningfully apply to CSS/config wiring, but every new interactive page still gets a render-level Vitest assertion proving it mounts and shows real content, matching the rigor Plan 1's `Login.test.jsx` already established.

**Interfaces already available:** `api` (Axios instance, `src/api/client.js`, attaches `Authorization: Bearer <token>`), `useAuth()` (`src/auth/AuthContext.jsx`, exposes `{user, token, login, register, logout}`), `ProtectedRoute` (`src/components/ProtectedRoute.jsx`). Backend endpoints already built and live: `GET /api/me`, `GET /api/me/credits`, `GET /api/sessions?filter=upcoming`, `GET /api/notifications/unread-count`.

---

### Task 1: Tailwind v4 + shadcn/ui foundation (Button, Card, Input, Label)

**Files:**
- Modify: `frontend/package.json` (add dependencies)
- Modify: `frontend/vite.config.js` (add Tailwind plugin + `@` path alias)
- Create: `frontend/jsconfig.json` (editor-level path alias, mirrors the Vite alias)
- Create: `frontend/src/index.css` (Tailwind import + shadcn CSS-variable theme, light + dark)
- Modify: `frontend/src/main.jsx` (import the new stylesheet)
- Create: `frontend/src/lib/utils.js` (the `cn()` class-merging helper every shadcn component uses)
- Create: `frontend/src/components/ui/button.jsx`
- Create: `frontend/src/components/ui/card.jsx`
- Create: `frontend/src/components/ui/input.jsx`
- Create: `frontend/src/components/ui/label.jsx`
- Create: `frontend/components.json` (shadcn config, so a human can run `npx shadcn add <x>` later and it lands correctly)
- Test: `frontend/src/components/ui/button.test.jsx`

**Interfaces:**
- Consumes: nothing new.
- Produces: `cn(...classes)` (from `@/lib/utils`) used by every shadcn component from here on. `Button` (variants: `default|destructive|outline|secondary|ghost|link`, sizes: `default|sm|lg|icon`, `asChild` prop). `Card`/`CardHeader`/`CardTitle`/`CardDescription`/`CardContent`/`CardFooter`. `Input` (styled `<input>`, forwards all native props + `ref`). `Label` (Radix `Label.Root`-based, correctly associates via `htmlFor`).

- [ ] **Step 1: Add the new dependencies**

In `frontend/package.json`, add to `dependencies`:
```json
    "@radix-ui/react-avatar": "^1.1.1",
    "@radix-ui/react-dropdown-menu": "^2.1.2",
    "@radix-ui/react-label": "^2.1.0",
    "@radix-ui/react-separator": "^1.1.0",
    "@radix-ui/react-slot": "^1.1.0",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.1.1",
    "lucide-react": "^0.454.0",
    "tailwind-merge": "^2.5.4",
```
and to `devDependencies`:
```json
    "@tailwindcss/vite": "^4.0.0",
    "tailwindcss": "^4.0.0",
```
(Keep every existing dependency exactly as it is — this is additive only.)

- [ ] **Step 2: Wire the Tailwind Vite plugin and path alias**

Replace `frontend/vite.config.js` with:
```js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
});
```

- [ ] **Step 3: Add the editor-level path alias**

`frontend/jsconfig.json`:
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

- [ ] **Step 4: Write the shadcn theme stylesheet**

`frontend/src/index.css`:
```css
@import "tailwindcss";

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-destructive-foreground: var(--destructive-foreground);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
}

:root {
  --radius: 0.625rem;
  --background: oklch(1 0 0);
  --foreground: oklch(0.145 0 0);
  --card: oklch(1 0 0);
  --card-foreground: oklch(0.145 0 0);
  --popover: oklch(1 0 0);
  --popover-foreground: oklch(0.145 0 0);
  --primary: oklch(0.205 0 0);
  --primary-foreground: oklch(0.985 0 0);
  --secondary: oklch(0.97 0 0);
  --secondary-foreground: oklch(0.205 0 0);
  --muted: oklch(0.97 0 0);
  --muted-foreground: oklch(0.556 0 0);
  --accent: oklch(0.97 0 0);
  --accent-foreground: oklch(0.205 0 0);
  --destructive: oklch(0.577 0.245 27.325);
  --destructive-foreground: oklch(0.985 0 0);
  --border: oklch(0.922 0 0);
  --input: oklch(0.922 0 0);
  --ring: oklch(0.708 0 0);
}

.dark {
  --background: oklch(0.145 0 0);
  --foreground: oklch(0.985 0 0);
  --card: oklch(0.205 0 0);
  --card-foreground: oklch(0.985 0 0);
  --popover: oklch(0.205 0 0);
  --popover-foreground: oklch(0.985 0 0);
  --primary: oklch(0.922 0 0);
  --primary-foreground: oklch(0.205 0 0);
  --secondary: oklch(0.269 0 0);
  --secondary-foreground: oklch(0.985 0 0);
  --muted: oklch(0.269 0 0);
  --muted-foreground: oklch(0.708 0 0);
  --accent: oklch(0.269 0 0);
  --accent-foreground: oklch(0.985 0 0);
  --destructive: oklch(0.704 0.191 22.216);
  --destructive-foreground: oklch(0.985 0 0);
  --border: oklch(1 0 0 / 10%);
  --input: oklch(1 0 0 / 15%);
  --ring: oklch(0.556 0 0);
}

body {
  background-color: var(--background);
  color: var(--foreground);
}
```

- [ ] **Step 5: Import the stylesheet**

In `frontend/src/main.jsx`, add this import as the first line:
```js
import './index.css';
```
(Keep every other line of the file exactly as it is.)

- [ ] **Step 6: Write the cn() utility**

`frontend/src/lib/utils.js`:
```js
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs) {
  return twMerge(clsx(inputs));
}
```

- [ ] **Step 7: Write the Button component**

`frontend/src/components/ui/button.jsx`:
```jsx
import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 outline-none focus-visible:ring-2 focus-visible:ring-ring",
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground shadow hover:bg-primary/90',
        destructive: 'bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive/90',
        outline: 'border border-input bg-background shadow-sm hover:bg-accent hover:text-accent-foreground',
        secondary: 'bg-secondary text-secondary-foreground shadow-sm hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        link: 'text-primary underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-md px-3 text-xs',
        lg: 'h-10 rounded-md px-8',
        icon: 'h-9 w-9',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  }
);

const Button = React.forwardRef(({ className, variant, size, asChild = false, ...props }, ref) => {
  const Comp = asChild ? Slot : 'button';
  return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />;
});
Button.displayName = 'Button';

export { Button, buttonVariants };
```

- [ ] **Step 8: Write the Card component**

`frontend/src/components/ui/card.jsx`:
```jsx
import * as React from 'react';

import { cn } from '@/lib/utils';

const Card = React.forwardRef(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('rounded-xl border bg-card text-card-foreground shadow', className)} {...props} />
));
Card.displayName = 'Card';

const CardHeader = React.forwardRef(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('flex flex-col space-y-1.5 p-6', className)} {...props} />
));
CardHeader.displayName = 'CardHeader';

const CardTitle = React.forwardRef(({ className, ...props }, ref) => (
  <h3 ref={ref} className={cn('font-semibold leading-none tracking-tight', className)} {...props} />
));
CardTitle.displayName = 'CardTitle';

const CardDescription = React.forwardRef(({ className, ...props }, ref) => (
  <p ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />
));
CardDescription.displayName = 'CardDescription';

const CardContent = React.forwardRef(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('p-6 pt-0', className)} {...props} />
));
CardContent.displayName = 'CardContent';

const CardFooter = React.forwardRef(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('flex items-center p-6 pt-0', className)} {...props} />
));
CardFooter.displayName = 'CardFooter';

export { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter };
```

- [ ] **Step 9: Write the Input component**

`frontend/src/components/ui/input.jsx`:
```jsx
import * as React from 'react';

import { cn } from '@/lib/utils';

const Input = React.forwardRef(({ className, type, ...props }, ref) => {
  return (
    <input
      type={type}
      className={cn(
        'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
        className
      )}
      ref={ref}
      {...props}
    />
  );
});
Input.displayName = 'Input';

export { Input };
```

- [ ] **Step 10: Write the Label component**

`frontend/src/components/ui/label.jsx`:
```jsx
import * as React from 'react';
import * as LabelPrimitive from '@radix-ui/react-label';
import { cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const labelVariants = cva(
  'text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70'
);

const Label = React.forwardRef(({ className, ...props }, ref) => (
  <LabelPrimitive.Root ref={ref} className={cn(labelVariants(), className)} {...props} />
));
Label.displayName = LabelPrimitive.Root.displayName;

export { Label };
```

- [ ] **Step 11: Write components.json (for a human running `npx shadcn add` later)**

`frontend/components.json`:
```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": false,
  "tsx": false,
  "tailwind": {
    "config": "",
    "css": "src/index.css",
    "baseColor": "neutral",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui"
  }
}
```

- [ ] **Step 12: Write a smoke test proving the foundation wires together**

`frontend/src/components/ui/button.test.jsx`:
```jsx
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
```

- [ ] **Step 13: Install dependencies and verify**

Run: `cd frontend && npm install`
Expected: installs cleanly (new deps only, no version conflicts with existing `react`/`react-dom`/`react-router-dom`/`axios`).

Run: `cd frontend && npm test`
Expected: PASS — the existing `Login.test.jsx` (unaffected by this task) plus the two new `button.test.jsx` cases, all green.

Run: `cd frontend && npm run build`
Expected: build succeeds with no Tailwind/Vite/import errors.

- [ ] **Step 14: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.js frontend/jsconfig.json \
        frontend/src/index.css frontend/src/main.jsx frontend/src/lib/utils.js \
        frontend/src/components/ui/button.jsx frontend/src/components/ui/card.jsx \
        frontend/src/components/ui/input.jsx frontend/src/components/ui/label.jsx \
        frontend/components.json frontend/src/components/ui/button.test.jsx
git commit -m "feat: add Tailwind v4 and shadcn/ui foundation (button, card, input, label)"
```

---

### Task 2: Authenticated app shell (header, nav, avatar menu)

**Files:**
- Modify: `frontend/src/auth/AuthContext.jsx` (fetch `/api/me` on mount when token exists but user is null)
- Create: `frontend/src/components/ui/avatar.jsx`
- Create: `frontend/src/components/ui/badge.jsx`
- Create: `frontend/src/components/ui/dropdown-menu.jsx`
- Create: `frontend/src/components/ui/separator.jsx`
- Create: `frontend/src/components/layout/AppShell.jsx`
- Create: `frontend/src/components/layout/Nav.jsx`
- Modify: `frontend/src/App.jsx` (wrap protected routes in `AppShell`)
- Test: `frontend/src/components/layout/Nav.test.jsx`

**Interfaces:**
- Consumes: `Button`, `Avatar`/`AvatarFallback`, `DropdownMenu` family, `Badge`, `Separator` (Task 1 + this task), `useAuth()` (extended).
- Produces: `AppShell` — a layout wrapper rendering `Nav` plus `<main>{children}</main>`; `Nav` — top bar with the SkillSwap Hub brand, primary links (Dashboard, Skills, Matches, Sessions, Forum), and a right-aligned avatar dropdown (shows `user.fullName`, a "Log out" item, and an "Admin" link only when `user.role === 'ADMIN'`). `useAuth()` now also fetches and populates `user` automatically on mount whenever a token is present.

- [ ] **Step 1: Extend AuthContext to hydrate `user` on mount**

Replace `frontend/src/auth/AuthContext.jsx` with:
```jsx
import { createContext, useContext, useEffect, useState } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [user, setUser] = useState(null);

  useEffect(() => {
    if (token && !user) {
      api.get('/me').then((res) => setUser(res.data)).catch(() => {});
    }
  }, [token, user]);

  function persist(res) {
    const { token: t, ...profile } = res.data;
    localStorage.setItem('token', t);
    setToken(t);
    setUser(profile);
    return profile;
  }

  async function login(email, password) {
    return persist(await api.post('/auth/login', { email, password }));
  }

  async function register(payload) {
    return persist(await api.post('/auth/register', payload));
  }

  function logout() {
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
```
(The only changes from the current file: the `useEffect` import/hook that hydrates `user` from `/api/me` when a token exists but `user` is still `null` — e.g. right after a hard page refresh. Every other line is unchanged.)

- [ ] **Step 2: Write the Avatar, Badge, DropdownMenu, Separator components**

`frontend/src/components/ui/avatar.jsx`:
```jsx
import * as React from 'react';
import * as AvatarPrimitive from '@radix-ui/react-avatar';

import { cn } from '@/lib/utils';

const Avatar = React.forwardRef(({ className, ...props }, ref) => (
  <AvatarPrimitive.Root
    ref={ref}
    className={cn('relative flex h-9 w-9 shrink-0 overflow-hidden rounded-full', className)}
    {...props}
  />
));
Avatar.displayName = AvatarPrimitive.Root.displayName;

const AvatarImage = React.forwardRef(({ className, ...props }, ref) => (
  <AvatarPrimitive.Image ref={ref} className={cn('aspect-square h-full w-full', className)} {...props} />
));
AvatarImage.displayName = AvatarPrimitive.Image.displayName;

const AvatarFallback = React.forwardRef(({ className, ...props }, ref) => (
  <AvatarPrimitive.Fallback
    ref={ref}
    className={cn('flex h-full w-full items-center justify-center rounded-full bg-muted text-sm font-medium', className)}
    {...props}
  />
));
AvatarFallback.displayName = AvatarPrimitive.Fallback.displayName;

export { Avatar, AvatarImage, AvatarFallback };
```

`frontend/src/components/ui/badge.jsx`:
```jsx
import * as React from 'react';
import { cva } from 'class-variance-authority';

import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        destructive: 'border-transparent bg-destructive text-destructive-foreground',
        outline: 'text-foreground',
      },
    },
    defaultVariants: { variant: 'default' },
  }
);

function Badge({ className, variant, ...props }) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { Badge, badgeVariants };
```

`frontend/src/components/ui/separator.jsx`:
```jsx
import * as React from 'react';
import * as SeparatorPrimitive from '@radix-ui/react-separator';

import { cn } from '@/lib/utils';

const Separator = React.forwardRef(
  ({ className, orientation = 'horizontal', decorative = true, ...props }, ref) => (
    <SeparatorPrimitive.Root
      ref={ref}
      decorative={decorative}
      orientation={orientation}
      className={cn(
        'shrink-0 bg-border',
        orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px',
        className
      )}
      {...props}
    />
  )
);
Separator.displayName = SeparatorPrimitive.Root.displayName;

export { Separator };
```

`frontend/src/components/ui/dropdown-menu.jsx`:
```jsx
import * as React from 'react';
import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu';

import { cn } from '@/lib/utils';

const DropdownMenu = DropdownMenuPrimitive.Root;
const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger;

const DropdownMenuContent = React.forwardRef(({ className, sideOffset = 4, ...props }, ref) => (
  <DropdownMenuPrimitive.Portal>
    <DropdownMenuPrimitive.Content
      ref={ref}
      sideOffset={sideOffset}
      className={cn(
        'z-50 min-w-[8rem] overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md',
        className
      )}
      {...props}
    />
  </DropdownMenuPrimitive.Portal>
));
DropdownMenuContent.displayName = DropdownMenuPrimitive.Content.displayName;

const DropdownMenuItem = React.forwardRef(({ className, ...props }, ref) => (
  <DropdownMenuPrimitive.Item
    ref={ref}
    className={cn(
      'relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      className
    )}
    {...props}
  />
));
DropdownMenuItem.displayName = DropdownMenuPrimitive.Item.displayName;

const DropdownMenuLabel = React.forwardRef(({ className, ...props }, ref) => (
  <DropdownMenuPrimitive.Label
    ref={ref}
    className={cn('px-2 py-1.5 text-sm font-semibold', className)}
    {...props}
  />
));
DropdownMenuLabel.displayName = DropdownMenuPrimitive.Label.displayName;

const DropdownMenuSeparator = React.forwardRef(({ className, ...props }, ref) => (
  <DropdownMenuPrimitive.Separator ref={ref} className={cn('-mx-1 my-1 h-px bg-muted', className)} {...props} />
));
DropdownMenuSeparator.displayName = DropdownMenuPrimitive.Separator.displayName;

export {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
};
```

- [ ] **Step 3: Write Nav**

`frontend/src/components/layout/Nav.jsx`:
```jsx
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthContext';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu';

const NAV_LINKS = [
  { to: '/', label: 'Dashboard' },
  { to: '/skills', label: 'Skills' },
  { to: '/matches', label: 'Matches' },
  { to: '/sessions', label: 'Sessions' },
  { to: '/forum', label: 'Forum' },
];

function initials(name) {
  if (!name) return '?';
  return name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

export default function Nav() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <header className="border-b bg-background">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <div className="flex items-center gap-6">
          <Link to="/" className="font-semibold">SkillSwap Hub</Link>
          <nav className="hidden gap-4 sm:flex" aria-label="Primary">
            {NAV_LINKS.map((link) => (
              <Link key={link.to} to={link.to} className="text-sm text-muted-foreground hover:text-foreground">
                {link.label}
              </Link>
            ))}
          </nav>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger className="outline-none" aria-label="Account menu">
            <Avatar>
              <AvatarFallback>{initials(user?.fullName)}</AvatarFallback>
            </Avatar>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel>{user?.fullName || 'Account'}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {user?.role === 'ADMIN' && (
              <DropdownMenuItem asChild>
                <Link to="/admin">Admin</Link>
              </DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={handleLogout}>Log out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
```

- [ ] **Step 4: Write AppShell**

`frontend/src/components/layout/AppShell.jsx`:
```jsx
import Nav from './Nav';

export default function AppShell({ children }) {
  return (
    <div className="min-h-screen bg-background">
      <Nav />
      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
    </div>
  );
}
```

- [ ] **Step 5: Write the Nav test**

`frontend/src/components/layout/Nav.test.jsx`:
```jsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '@/auth/AuthContext';
import Nav from './Nav';

test('renders the brand and primary navigation links', () => {
  render(
    <AuthProvider>
      <MemoryRouter>
        <Nav />
      </MemoryRouter>
    </AuthProvider>
  );
  expect(screen.getByText('SkillSwap Hub')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Skills' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Sessions' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /account menu/i })).toBeInTheDocument();
});
```

- [ ] **Step 6: Wire AppShell into App.jsx's protected route**

Replace `frontend/src/App.jsx` with:
```jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppShell from './components/layout/AppShell';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <AppShell>
                  <Dashboard />
                </AppShell>
              </ProtectedRoute>
            }
          />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
```
(Later UI plans will add more `<Route>` entries for `/skills`, `/matches`, `/sessions`, `/forum`, `/admin`, each wrapped the same way — this plan only wires the shell around the one route that already exists.)

- [ ] **Step 7: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — `Login.test.jsx`, `button.test.jsx` (2 cases), `Nav.test.jsx` all green.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/auth/AuthContext.jsx frontend/src/components/ui/avatar.jsx \
        frontend/src/components/ui/badge.jsx frontend/src/components/ui/dropdown-menu.jsx \
        frontend/src/components/ui/separator.jsx frontend/src/components/layout/AppShell.jsx \
        frontend/src/components/layout/Nav.jsx frontend/src/components/layout/Nav.test.jsx \
        frontend/src/App.jsx
git commit -m "feat: add authenticated app shell with nav and account menu"
```

---

### Task 3: Restyled Login + Register pages

**Files:**
- Modify: `frontend/src/pages/Login.jsx`
- Modify: `frontend/src/pages/Register.jsx`

**Interfaces:**
- Consumes: `Button`, `Card`/`CardHeader`/`CardTitle`/`CardDescription`/`CardContent`/`CardFooter`, `Input`, `Label` (Task 1); `useAuth()` (unchanged usage — `login`/`register` still called the same way).
- Produces: no new exports — same two page components, same routes, same behavior; only the markup/styling changes. `Login.test.jsx` (Plan 1, untouched) must still pass unmodified.

**Business rule:** preserve every existing behavior exactly — `onSubmit` handlers, error message display (`role="alert"`), navigation to `/` on success, the `Link` to the other auth page. Only the JSX/styling changes.

- [ ] **Step 1: Restyle Login**

Replace `frontend/src/pages/Login.jsx` with:
```jsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardHeader, CardDescription, CardContent, CardFooter } from '@/components/ui/card';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message ?? 'Login failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-sm">
        <form onSubmit={onSubmit}>
          <CardHeader>
            <h1 className="font-semibold leading-none tracking-tight">Log in</h1>
            <CardDescription>Welcome back to SkillSwap Hub.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </div>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </CardContent>
          <CardFooter className="flex flex-col gap-4">
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Logging in…' : 'Log in'}
            </Button>
            <p className="text-sm text-muted-foreground">
              No account? <Link to="/register" className="text-primary underline-offset-4 hover:underline">Register</Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
```
(The page's top-level heading is a plain `<h1>` styled to match `CardTitle`'s look, not `CardTitle` itself — this component's hand-written `CardTitle` doesn't support Radix `asChild`/`Slot`, so using it directly here keeps the accessible `<h1>` element `Login.test.jsx` looks for via `getByRole('heading', {name: /log in/i})` without relying on a feature the component doesn't have.)

- [ ] **Step 2: Restyle Register**

Replace `frontend/src/pages/Register.jsx` with:
```jsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardHeader, CardDescription, CardContent, CardFooter } from '@/components/ui/card';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function update(field) {
    return (e) => setForm({ ...form, [field]: e.target.value });
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await register(form);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message ?? 'Registration failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-sm">
        <form onSubmit={onSubmit}>
          <CardHeader>
            <h1 className="font-semibold leading-none tracking-tight">Create account</h1>
            <CardDescription>Start exchanging skills for free.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="fullName">Full name</Label>
              <Input id="fullName" value={form.fullName} onChange={update('fullName')} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={form.email} onChange={update('email')} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" value={form.password} onChange={update('password')} minLength={8} required />
            </div>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </CardContent>
          <CardFooter className="flex flex-col gap-4">
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? 'Creating account…' : 'Register'}
            </Button>
            <p className="text-sm text-muted-foreground">
              Have an account? <Link to="/login" className="text-primary underline-offset-4 hover:underline">Log in</Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}
```

- [ ] **Step 3: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — `Login.test.jsx` (Plan 1's original, unmodified assertions) must still pass against the new markup, plus `button.test.jsx`/`Nav.test.jsx`.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/Login.jsx frontend/src/pages/Register.jsx
git commit -m "feat: restyle login and register pages with shadcn/ui"
```

---

### Task 4: Real Dashboard (credits, upcoming sessions, unread notifications)

**Files:**
- Modify: `frontend/src/pages/Dashboard.jsx`
- Test: `frontend/src/pages/Dashboard.test.jsx`

**Interfaces:**
- Consumes: `api` (`src/api/client.js`), `useAuth()`, `Card` family, `Badge` (Task 1/2). Backend: `GET /api/me` (already used), `GET /api/me/credits` → `{totalCredits, creditsEarned, creditsSpent}`, `GET /api/sessions?filter=upcoming` → `SessionDto[]`, `GET /api/notifications/unread-count` → `{count}`.
- Produces: `Dashboard` renders three stat cards (Credits, Upcoming Sessions, Unread Notifications) fetched on mount, plus the existing welcome message and logout button (kept from Plan 1).

**Business rule:** each of the three stat fetches is independent — if one endpoint is slow/fails, the other two still render (matches this app's existing "no fatal dependency between widgets" pattern; each `useEffect`/`api.get` call has its own state and its own `.catch(() => {})`, exactly like Plan 1's original `Dashboard` did for `/me`).

- [ ] **Step 1: Write the failing Dashboard test**

`frontend/src/pages/Dashboard.test.jsx`:
```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Dashboard from './Dashboard';

vi.mock('../api/client');

test('renders credits, upcoming sessions, and unread notification counts', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/me') return Promise.resolve({ data: { id: 1, fullName: 'Deva', email: 'd@example.com', role: 'USER' } });
    if (url === '/me/credits') return Promise.resolve({ data: { totalCredits: 12, creditsEarned: 3, creditsSpent: 1 } });
    if (url === '/sessions') return Promise.resolve({ data: [{ id: 1 }, { id: 2 }] });
    if (url === '/notifications/unread-count') return Promise.resolve({ data: { count: 4 } });
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getByText('Deva')).toBeInTheDocument());
  expect(screen.getByText('12')).toBeInTheDocument();
  expect(screen.getByText('2')).toBeInTheDocument();
  expect(screen.getByText('4')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Dashboard`
Expected: FAIL — current `Dashboard.jsx` only fetches `/me`, never renders credits/sessions/notifications counts.

- [ ] **Step 3: Rewrite Dashboard**

Replace `frontend/src/pages/Dashboard.jsx` with:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';

export default function Dashboard() {
  const { logout } = useAuth();
  const [profile, setProfile] = useState(null);
  const [credits, setCredits] = useState(null);
  const [upcomingCount, setUpcomingCount] = useState(null);
  const [unreadCount, setUnreadCount] = useState(null);

  useEffect(() => {
    api.get('/me').then((res) => setProfile(res.data)).catch(() => {});
    api.get('/me/credits').then((res) => setCredits(res.data)).catch(() => {});
    api.get('/sessions', { params: { filter: 'upcoming' } })
      .then((res) => setUpcomingCount(res.data.length))
      .catch(() => {});
    api.get('/notifications/unread-count').then((res) => setUnreadCount(res.data.count)).catch(() => {});
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        {profile && <p className="text-muted-foreground">Welcome, {profile.fullName}</p>}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Credits</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{credits ? credits.totalCredits : '—'}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Upcoming Sessions</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{upcomingCount !== null ? upcomingCount : '—'}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Unread Notifications</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{unreadCount !== null ? unreadCount : '—'}</p>
          </CardContent>
        </Card>
      </div>

      <button onClick={logout} className="text-sm text-muted-foreground underline-offset-4 hover:underline">
        Log out
      </button>
    </div>
  );
}
```
(The logout control stays a plain text link-style button here deliberately — Task 2's `Nav` already has the primary "Log out" action in the account dropdown; this one is a redundant convenience left from Plan 1, kept simple rather than promoted to a styled `Button` to avoid two competing primary actions on the page, per this app's "one primary CTA per screen" principle.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Dashboard`
Expected: PASS.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — all frontend tests green (`Login`, `button`, `Nav`, `Dashboard`).

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Dashboard.jsx frontend/src/pages/Dashboard.test.jsx
git commit -m "feat: build real dashboard with credits, sessions, and notification stats"
```

---

## Self-Review

**Spec coverage (UI Plan 1 slice):** Tailwind + shadcn/ui foundation, app shell with nav, restyled auth pages, live dashboard — matches spec §5.6's Dashboard/Login description (welcome message, quick stats: credits, upcoming sessions; nav bar with profile menu). Full sidebar-style navigation and "recommended matches" widget are deferred to later UI plans (Skills+Matching UI), consistent with backend Plan 2 not existing in any UI yet.

**Placeholder scan:** No TBD/TODO; every step has complete code.

**Type consistency:** `AuthContext`'s exposed shape (`{user, token, login, register, logout}`) is unchanged — only its internal hydration behavior gains a `useEffect`, so every existing consumer (`Login`, `Register`, `ProtectedRoute`, the original `Dashboard`) keeps working unmodified. `cn()` signature/usage is identical across all 8 new/modified shadcn component files. `Nav`'s `user?.role === 'ADMIN'` check matches the exact string the backend's `Role` enum serializes (`"ADMIN"`, confirmed against `AdminUserDto.role()`/JWT flow in Plans 1 and 6).

**Scope check:** Four tasks — foundation, shell, auth restyle, dashboard — each independently buildable/testable, sized similarly to the backend plans' task granularity.

**Deliberate simplifications (flagged for the record):**
- No dark-mode toggle UI yet — the `.dark` CSS variables exist (Task 1) but nothing switches the class; deferred until a settings/preferences page exists. `ponytail: dark tokens defined but unused, wire a toggle when there's a natural home for it.`
- Nav's primary links (`/skills`, `/matches`, `/sessions`, `/forum`) don't have routes/pages yet — they'll 404 until the next UI plans add them. This is intentional: the nav is being built once, correctly, now, rather than re-touched in every subsequent plan.
- No loading skeletons on the Dashboard stat cards (shows `—` while pending) — matches this app's existing minimal-loading-state convention from Plan 1; upgrade path noted, not built speculatively.
