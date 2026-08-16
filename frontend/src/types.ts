export interface SagaView {
    id: string;
    fromAccount: string;
    toAccount: string;
    amount: number;
    state: string;
    failStep: string;
    detail: string;
    updatedAt: string;
}