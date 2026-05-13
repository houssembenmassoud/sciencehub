# ScienceHub UI

## Features

- **Dashboard**: Overview of articles, signatures, and quick actions
- **Article Management**: Create, view, and manage scientific articles
- **E-Signatures**: Sign documents with PKI-based cryptographic signatures
- **Certificate Management**: View and manage digital certificates
- **Verification**: Verify document signatures and integrity

## Tech Stack

- **Vue 3** - Progressive JavaScript framework
- **TypeScript** - Type-safe JavaScript
- **Pinia** - State management
- **Vue Router** - Client-side routing
- **Axios** - HTTP client
- **PrimeVue** - UI component library
- **Tailwind CSS** - Utility-first CSS framework
- **Vite** - Next-generation build tool


### Installation

```bash
cd sciencehub-ui
npm install
```

### Development

```bash
npm run dev
```

The app will be available at `http://localhost:3000`

### Build for Production

```bash
npm run build
```

### Preview Production Build

```bash
npm run preview
```

## Project Structure

```
sciencehub-ui/
├── src/
│   ├── assets/          # Static assets and global styles
│   ├── components/      # Reusable Vue components
│   ├── composables/     # Composable functions
│   ├── router/          # Vue Router configuration
│   ├── services/        # API service layer
│   ├── stores/          # Pinia state stores
│   ├── types/           # TypeScript type definitions
│   ├── views/           # Page components
│   ├── App.vue          # Root component
│   └── main.ts          # Application entry point
├── index.html
├── package.json
├── tailwind.config.js
├── tsconfig.json
└── vite.config.ts
```

## API Integration

The frontend connects to the ScienceHub backend API at `http://localhost:8080/api`.

### Available Endpoints

- `/api/articles` - Article CRUD operations
- `/api/signatures/*` - Signature and verification operations
- `/api/certificates/*` - Certificate management
- `/api/pipeline/*` - Approval pipeline operations
- `/api/auth/*` - Authentication operations

## Environment Variables

Create a `.env` file in the root directory:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```
