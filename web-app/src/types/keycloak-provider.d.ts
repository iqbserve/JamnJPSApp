
/**
 * Local Interface to work with Keycloak
 */
export interface KeycloakIFace {
    init(options: unknown): Promise<boolean>;
    login(): boolean;
    logout(): void;
    updateToken(minValidity: number): Promise<boolean>;
    loadUserProfile(): Promise<unknown>;
    token: string;
}

